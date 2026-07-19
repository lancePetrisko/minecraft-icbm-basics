package com.example.icbmbasics.screen;

import java.util.ArrayList;
import java.util.List;

import com.example.icbmbasics.block.entity.RadarBlockEntity;
import com.example.icbmbasics.network.RadarContact;
import com.example.icbmbasics.network.RadarLogEntry;
import com.example.icbmbasics.network.RadarScreenData;
import com.example.icbmbasics.registry.ModScreenHandlers;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.BlockPos;

import org.jetbrains.annotations.Nullable;

/**
 * Backs the radar's scope GUI. No slots: it's a pure read-only view over the
 * block entity's tracked contacts and impact log, refreshed live via
 * {@code RadarUpdatePayload} while open.
 */
public class RadarScreenHandler extends ScreenHandler {
	private final BlockPos radarPos;
	private final int tier;
	private final int detectionRadius;
	/** Only set server-side, where the handler was built straight from the block entity. */
	@Nullable
	private final RadarBlockEntity radar;

	private List<RadarContact> contacts;
	private List<RadarLogEntry> log;

	/** Client-side constructor, called by the ExtendedScreenHandlerType. */
	public RadarScreenHandler(int syncId, PlayerInventory playerInventory, RadarScreenData data) {
		this(syncId, data.pos(), data.tier(), data.detectionRadius(), null, data.contacts(), data.log());
	}

	/** Server-side constructor, called by the block entity. */
	public RadarScreenHandler(int syncId, PlayerInventory playerInventory, RadarBlockEntity radar, BlockPos pos) {
		this(syncId, pos, radar.getTier(), radar.getDetectionRadius(), radar,
				radar.getContactsSnapshot(), radar.getLogSnapshot());
	}

	private RadarScreenHandler(int syncId, BlockPos pos, int tier, int detectionRadius,
			@Nullable RadarBlockEntity radar, List<RadarContact> contacts, List<RadarLogEntry> log) {
		super(ModScreenHandlers.RADAR, syncId);
		this.radarPos = pos;
		this.tier = tier;
		this.detectionRadius = detectionRadius;
		this.radar = radar;
		this.contacts = new ArrayList<>(contacts);
		this.log = new ArrayList<>(log);
	}

	public BlockPos getRadarPos() {
		return this.radarPos;
	}

	public int getTier() {
		return this.tier;
	}

	public int getDetectionRadius() {
		return this.detectionRadius;
	}

	public List<RadarContact> getContacts() {
		return this.contacts;
	}

	public List<RadarLogEntry> getLog() {
		return this.log;
	}

	public void setContacts(List<RadarContact> contacts) {
		this.contacts = new ArrayList<>(contacts);
	}

	public void setLog(List<RadarLogEntry> log) {
		this.log = new ArrayList<>(log);
	}

	@Override
	public boolean canUse(PlayerEntity player) {
		return player.getBlockPos().isWithinDistance(this.radarPos, 8.0);
	}

	@Override
	public void onClosed(PlayerEntity player) {
		super.onClosed(player);
		if (this.radar != null && player instanceof ServerPlayerEntity serverPlayer) {
			this.radar.removeViewer(serverPlayer);
		}
	}

	@Override
	public ItemStack quickMove(PlayerEntity player, int slotIndex) {
		return ItemStack.EMPTY;
	}
}
