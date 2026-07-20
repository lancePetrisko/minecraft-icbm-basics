package com.example.icbmbasics.block.entity;

import com.example.icbmbasics.ICBMBasics;
import com.example.icbmbasics.block.WireNetwork;
import com.example.icbmbasics.entity.MissileEntity;
import com.example.icbmbasics.entity.SamInterceptorEntity;
import com.example.icbmbasics.network.AmmoScreenData;
import com.example.icbmbasics.registry.ModBlockEntities;
import com.example.icbmbasics.registry.ModEntities;
import com.example.icbmbasics.registry.ModItems;
import com.example.icbmbasics.screen.SamAmmoScreenHandler;

import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerFactory;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.Inventories;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.storage.ReadView;
import net.minecraft.storage.WriteView;
import net.minecraft.text.Text;
import net.minecraft.util.collection.DefaultedList;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;

/**
 * Always-on ground-to-air defense: every {@link #SCAN_INTERVAL_TICKS} it
 * looks for the nearest missile in range that isn't still on its own pad
 * (same age-based "outgoing" rule radar uses) and, once its cooldown is
 * ready, launches a homing {@link SamInterceptorEntity} at it. No GUI - this
 * is a fire-and-forget defensive structure, unlike the radar it shares
 * detection logic with.
 *
 * <p>SAM sites coordinate through {@link #CLAIMED_TARGETS}, a world-wide set
 * of missile UUIDs that already have an interceptor in flight toward them -
 * not scoped to any particular site's radius. A missile is claimed the
 * instant a site fires on it and released by {@link SamInterceptorEntity}
 * once that interceptor resolves (hit, miss, lost target, or timeout), so no
 * two sites ever waste a rocket on the same missile at once.
 *
 * <p>Single-slot {@link Inventory} for {@code ICBM_BASICS.SAM_AMMO} - hopper
 * fed, or right-click opens a small GUI (just the slot + player inventory) to
 * see/refill the count.
 *
 * <p>Only fires while {@link WireNetwork#isConnectedToRadar} finds a path to a
 * radar - direct adjacency or a chain of {@code WIRE} blocks. Unconnected
 * sites just sit idle regardless of ammo/cooldown.
 */
public class SamSiteBlockEntity extends BlockEntity
		implements Inventory, ExtendedScreenHandlerFactory<AmmoScreenData> {
	private static final int SCAN_INTERVAL_TICKS = 10;
	private static final int LAUNCH_ACQUIRE_AGE_TICKS = 20;
	public static final int AMMO_SLOT = 0;

	private static final Map<ServerWorld, Set<UUID>> CLAIMED_TARGETS = new WeakHashMap<>();

	private final DefaultedList<ItemStack> inventory = DefaultedList.ofSize(1, ItemStack.EMPTY);
	private int cooldown;

	public SamSiteBlockEntity(BlockPos pos, BlockState state) {
		super(ModBlockEntities.SAM_SITE, pos, state);
	}

	public static void tick(World world, BlockPos pos, BlockState state, SamSiteBlockEntity site) {
		if (!(world instanceof ServerWorld serverWorld)) {
			return;
		}
		if (site.cooldown > 0) {
			site.cooldown--;
		}
		if (world.getTime() % SCAN_INTERVAL_TICKS != 0 || site.cooldown > 0) {
			return;
		}
		if (site.inventory.get(AMMO_SLOT).isEmpty()) {
			return;
		}
		if (!WireNetwork.isConnectedToRadar(world, pos)) {
			return;
		}

		double centerX = pos.getX() + 0.5;
		double centerY = pos.getY() + 0.5;
		double centerZ = pos.getZ() + 0.5;

		Set<UUID> claimed = CLAIMED_TARGETS.getOrDefault(serverWorld, Set.of());

		// A missile's own radar cross-section (smaller for cruise missiles)
		// shrinks the effective detection radius used against it specifically.
		MissileEntity target = null;
		double bestDistanceSq = Double.MAX_VALUE;
		for (MissileEntity missile : MissileEntity.getActiveMissiles(serverWorld)) {
			if (missile.getFlightAge() <= LAUNCH_ACQUIRE_AGE_TICKS || claimed.contains(missile.getUuid())) {
				continue;
			}
			double effectiveRadius = ICBMBasics.CONFIG.samDetectionRadius * missile.getRadarCrossSectionMultiplier();
			double distanceSq = missile.squaredDistanceTo(centerX, centerY, centerZ);
			if (distanceSq <= effectiveRadius * effectiveRadius && distanceSq < bestDistanceSq) {
				bestDistanceSq = distanceSq;
				target = missile;
			}
		}

		if (target == null) {
			return;
		}

		claim(serverWorld, target.getUuid());

		SamInterceptorEntity interceptor = new SamInterceptorEntity(ModEntities.SAM_INTERCEPTOR, serverWorld);
		interceptor.refreshPositionAndAngles(centerX, centerY + 1.0, centerZ, 0.0f, 0.0f);
		interceptor.setTarget(target);
		serverWorld.spawnEntity(interceptor);

		serverWorld.playSound(null, pos, SoundEvents.ENTITY_FIREWORK_ROCKET_LAUNCH, SoundCategory.HOSTILE, 3.0f, 1.3f);
		site.inventory.get(AMMO_SLOT).decrement(1);
		site.cooldown = ICBMBasics.CONFIG.samFireCooldownTicks;
		site.markDirty();
	}

	// --------------------------------------------------------------- inventory

	@Override
	public int size() {
		return this.inventory.size();
	}

	@Override
	public boolean isEmpty() {
		return this.inventory.get(AMMO_SLOT).isEmpty();
	}

	@Override
	public ItemStack getStack(int slot) {
		return this.inventory.get(slot);
	}

	@Override
	public ItemStack removeStack(int slot, int amount) {
		ItemStack result = Inventories.splitStack(this.inventory, slot, amount);
		if (!result.isEmpty()) {
			this.markDirty();
		}
		return result;
	}

	@Override
	public ItemStack removeStack(int slot) {
		return Inventories.removeStack(this.inventory, slot);
	}

	@Override
	public void setStack(int slot, ItemStack stack) {
		this.inventory.set(slot, stack);
		this.markDirty();
	}

	@Override
	public boolean isValid(int slot, ItemStack stack) {
		return stack.isOf(ModItems.SAM_AMMO);
	}

	@Override
	public boolean canPlayerUse(PlayerEntity player) {
		return Inventory.canPlayerUse(this, player);
	}

	@Override
	public void clear() {
		this.inventory.clear();
	}

	private static void claim(ServerWorld world, UUID missileId) {
		CLAIMED_TARGETS.computeIfAbsent(world, w -> new HashSet<>()).add(missileId);
	}

	/** Called by {@link SamInterceptorEntity} once its shot at this missile resolves, one way or another. */
	public static void releaseClaim(ServerWorld world, UUID missileId) {
		Set<UUID> claimed = CLAIMED_TARGETS.get(world);
		if (claimed != null) {
			claimed.remove(missileId);
		}
	}

	// --------------------------------------------------------------------- gui

	@Override
	public Text getDisplayName() {
		return Text.translatable("block.icbmbasics.sam_site");
	}

	@Override
	@Nullable
	public ScreenHandler createMenu(int syncId, PlayerInventory playerInventory, PlayerEntity player) {
		return new SamAmmoScreenHandler(syncId, playerInventory, this, this.getPos());
	}

	@Override
	public AmmoScreenData getScreenOpeningData(ServerPlayerEntity player) {
		return new AmmoScreenData(this.getPos());
	}

	// ---------------------------------------------------------------------- nbt

	@Override
	protected void writeData(WriteView view) {
		super.writeData(view);
		view.putInt("Cooldown", this.cooldown);
		Inventories.writeData(view, this.inventory);
	}

	@Override
	protected void readData(ReadView view) {
		super.readData(view);
		this.cooldown = view.getInt("Cooldown", 0);
		this.inventory.clear();
		Inventories.readData(view, this.inventory);
	}
}
