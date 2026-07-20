package com.example.icbmbasics.block.entity;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.example.icbmbasics.ICBMBasics;
import com.example.icbmbasics.block.RadarBlock;
import com.example.icbmbasics.entity.MissileEntity;
import com.example.icbmbasics.network.RadarContact;
import com.example.icbmbasics.network.RadarLogEntry;
import com.example.icbmbasics.network.RadarScreenData;
import com.example.icbmbasics.network.RadarUpdatePayload;
import com.example.icbmbasics.registry.ModBlockEntities;
import com.example.icbmbasics.screen.RadarScreenHandler;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerFactory;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import org.jetbrains.annotations.Nullable;

/**
 * Scans for {@link MissileEntity missiles} within range every {@link #SCAN_INTERVAL_TICKS}
 * ticks and pushes the contact list + impact log to whoever has the GUI open.
 *
 * <p>A missile first spotted while very young (still on the pad, see
 * {@link #LAUNCH_ACQUIRE_AGE_TICKS}) is treated as "ours": it stays tracked
 * map-wide, regardless of distance, until it resolves. Anything else is
 * "incoming" - only shown while it's actually within {@link #detectionRadius}.
 */
public class RadarBlockEntity extends BlockEntity implements ExtendedScreenHandlerFactory<RadarScreenData> {
	private static final int SCAN_INTERVAL_TICKS = 10;
	private static final int LAUNCH_ACQUIRE_AGE_TICKS = 20;
	private static final int MAX_LOG_ENTRIES = 12;
	/**
	 * How long a monitor's ping keeps this radar scanning even with no GUI open.
	 * A few times {@code MonitorBlockEntity}'s own scan interval, so a monitor
	 * that's still linked never lets the radar go idle between its own pings.
	 */
	private static final int MONITOR_ACTIVE_WINDOW_TICKS = 40;

	private final int tier;
	private final int detectionRadius;

	/** Missiles currently tracked, keyed by entity UUID. Transient: entities don't survive a restart anyway. */
	private final Map<UUID, MissileEntity> acquired = new HashMap<>();
	private final Map<UUID, Boolean> outgoingFlags = new HashMap<>();
	private final Deque<RadarLogEntry> log = new ArrayDeque<>();
	private final Set<ServerPlayerEntity> viewers = new HashSet<>();
	/** Last world tick a linked {@code MonitorBlockEntity} pinged this radar. Not persisted - self-expiring. */
	private long lastMonitorPingTick = Long.MIN_VALUE;

	public RadarBlockEntity(BlockPos pos, BlockState state) {
		super(ModBlockEntities.RADAR, pos, state);
		this.tier = state.getBlock() instanceof RadarBlock radarBlock ? radarBlock.getTier() : 1;
		int[] radii = ICBMBasics.CONFIG.radarTierDetectionRadii;
		this.detectionRadius = radii[Math.min(Math.max(this.tier - 1, 0), radii.length - 1)];
	}

	public int getTier() {
		return this.tier;
	}

	public int getDetectionRadius() {
		return this.detectionRadius;
	}

	public List<RadarContact> getContactsSnapshot() {
		return this.buildContacts();
	}

	public List<RadarLogEntry> getLogSnapshot() {
		return List.copyOf(this.log);
	}

	public void addViewer(ServerPlayerEntity player) {
		this.viewers.add(player);
	}

	public void removeViewer(ServerPlayerEntity player) {
		this.viewers.remove(player);
	}

	/** Called by a linked {@code MonitorBlockEntity} each time it scans, keeping this radar active without a GUI open. */
	public void pulseFromMonitor(long currentTick) {
		this.lastMonitorPingTick = currentTick;
	}

	private List<RadarContact> buildContacts() {
		List<RadarContact> contacts = new ArrayList<>(this.acquired.size());
		for (MissileEntity missile : this.acquired.values()) {
			boolean outgoing = this.outgoingFlags.getOrDefault(missile.getUuid(), false);
			contacts.add(new RadarContact(missile.getUuid(), missile.getX(), missile.getY(), missile.getZ(), outgoing));
		}
		return contacts;
	}

	// ---------------------------------------------------------------- ticking

	public static void tick(World world, BlockPos pos, BlockState state, RadarBlockEntity radar) {
		if (!(world instanceof ServerWorld serverWorld)) {
			return;
		}
		boolean monitorsActive = world.getTime() - radar.lastMonitorPingTick <= MONITOR_ACTIVE_WINDOW_TICKS;
		if (radar.viewers.isEmpty() && !monitorsActive) {
			return;
		}
		if (world.getTime() % SCAN_INTERVAL_TICKS != 0) {
			return;
		}

		Set<MissileEntity> active = MissileEntity.getActiveMissiles(serverWorld);

		// Acquire new contacts within range. A missile's own radar cross-section
		// (smaller for cruise missiles) shrinks the effective detection radius
		// used against it specifically, rather than radar's own radius changing.
		for (MissileEntity missile : active) {
			UUID id = missile.getUuid();
			if (radar.acquired.containsKey(id)) {
				continue;
			}
			double effectiveRadius = radar.detectionRadius * missile.getRadarCrossSectionMultiplier();
			if (missile.squaredDistanceTo(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) <= effectiveRadius * effectiveRadius) {
				radar.acquired.put(id, missile);
				radar.outgoingFlags.put(id, missile.getFlightAge() <= LAUNCH_ACQUIRE_AGE_TICKS);
			}
		}

		// Drop contacts that resolved (no longer active) or drifted out of range
		// (incoming-only contacts aren't kept once they leave the scope).
		boolean changed = false;
		for (var it = radar.acquired.entrySet().iterator(); it.hasNext(); ) {
			Map.Entry<UUID, MissileEntity> entry = it.next();
			MissileEntity missile = entry.getValue();

			if (!active.contains(missile)) {
				radar.logImpact(missile);
				radar.outgoingFlags.remove(entry.getKey());
				it.remove();
				changed = true;
				continue;
			}

			boolean outgoing = radar.outgoingFlags.getOrDefault(entry.getKey(), false);
			double effectiveRadius = radar.detectionRadius * missile.getRadarCrossSectionMultiplier();
			if (!outgoing && missile.squaredDistanceTo(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) > effectiveRadius * effectiveRadius) {
				radar.outgoingFlags.remove(entry.getKey());
				it.remove();
				changed = true;
			}
		}

		if (!radar.acquired.isEmpty() || changed || !radar.log.isEmpty()) {
			RadarUpdatePayload payload = new RadarUpdatePayload(pos, radar.buildContacts(), List.copyOf(radar.log));
			for (ServerPlayerEntity viewer : radar.viewers) {
				ServerPlayNetworking.send(viewer, payload);
			}
		}
	}

	private void logImpact(MissileEntity missile) {
		this.log.addFirst(new RadarLogEntry(
				(int) Math.floor(missile.getX()), (int) Math.floor(missile.getY()), (int) Math.floor(missile.getZ())));
		while (this.log.size() > MAX_LOG_ENTRIES) {
			this.log.removeLast();
		}
	}

	// --------------------------------------------------------------------- gui

	@Override
	public Text getDisplayName() {
		return Text.translatable("block.icbmbasics.radar_mk1");
	}

	@Override
	@Nullable
	public ScreenHandler createMenu(int syncId, PlayerInventory playerInventory, PlayerEntity player) {
		if (player instanceof ServerPlayerEntity serverPlayer) {
			this.addViewer(serverPlayer);
		}
		return new RadarScreenHandler(syncId, playerInventory, this, this.getPos());
	}

	@Override
	public RadarScreenData getScreenOpeningData(ServerPlayerEntity player) {
		return new RadarScreenData(this.getPos(), this.tier, this.detectionRadius,
				this.getContactsSnapshot(), this.getLogSnapshot());
	}
}
