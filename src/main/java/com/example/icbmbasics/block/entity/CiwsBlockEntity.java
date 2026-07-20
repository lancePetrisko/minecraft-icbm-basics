package com.example.icbmbasics.block.entity;

import com.example.icbmbasics.ICBMBasics;
import com.example.icbmbasics.block.WireNetwork;
import com.example.icbmbasics.entity.CiwsBulletEntity;
import com.example.icbmbasics.entity.MissileEntity;
import com.example.icbmbasics.network.AmmoScreenData;
import com.example.icbmbasics.registry.ModBlockEntities;
import com.example.icbmbasics.registry.ModEntities;
import com.example.icbmbasics.registry.ModItems;
import com.example.icbmbasics.screen.CiwsAmmoScreenHandler;

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
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import org.jetbrains.annotations.Nullable;

/**
 * A close-in weapon system: long range but low per-burst accuracy, very fast
 * reload ({@code ICBMConfig.ciwsRoundsPerSecond}, default 50/sec - scaled
 * down from a real Phalanx's ~75/sec, but well past the 20/sec a plain
 * per-tick cooldown could manage, via {@link #fireAccumulator}). Each burst
 * spawns a handful of {@link CiwsBulletEntity} tracer rounds (rendered as
 * small flying gray-concrete "bullets") aimed at a computed firing solution
 * ({@link #computeLeadPoint}), not the target's current position, so it
 * visually leads a moving missile the way a real fire-control computer
 * would. Shares the radar/SAM's age-based rule for ignoring missiles still on
 * their own pad. Rolls {@code ICBMConfig.ciwsAccuracy} once per burst - one
 * round is marked as the one that actually lands, and it only resolves the
 * hit once it visually arrives, not the instant the burst fires.
 *
 * <p>Single-slot {@link Inventory} for {@code ICBM_BASICS.CIWS_AMMO} - hopper
 * fed, or right-click opens a small GUI (just the slot + player inventory) to
 * see/refill the count.
 *
 * <p>Only fires while {@link WireNetwork#isConnectedToRadar} finds a path to a
 * radar - direct adjacency or a chain of {@code WIRE} blocks. Unconnected
 * CIWS just sit idle regardless of ammo/rate.
 */
public class CiwsBlockEntity extends BlockEntity
		implements Inventory, ExtendedScreenHandlerFactory<AmmoScreenData> {
	private static final int LAUNCH_ACQUIRE_AGE_TICKS = 20;
	/** Hard safety cap on bursts fired in a single tick, regardless of how high ciwsRoundsPerSecond is set. */
	private static final int MAX_BURSTS_PER_TICK = 20;
	public static final int AMMO_SLOT = 0;

	private final DefaultedList<ItemStack> inventory = DefaultedList.ofSize(1, ItemStack.EMPTY);
	/**
	 * Fractional "rounds owed" - a tick-integer cooldown can't express more
	 * than 20 bursts/sec, so this accumulates {@code ciwsRoundsPerSecond / 20}
	 * every tick and fires off however many whole bursts that adds up to
	 * (possibly more than one per tick at high rates).
	 */
	private double fireAccumulator;

	public CiwsBlockEntity(BlockPos pos, BlockState state) {
		super(ModBlockEntities.CIWS, pos, state);
	}

	public static void tick(World world, BlockPos pos, BlockState state, CiwsBlockEntity ciws) {
		if (!(world instanceof ServerWorld serverWorld)) {
			return;
		}

		ciws.fireAccumulator += ICBMBasics.CONFIG.ciwsRoundsPerSecond / 20.0;
		if (ciws.fireAccumulator < 1.0) {
			return;
		}
		if (ciws.inventory.get(AMMO_SLOT).isEmpty()) {
			ciws.fireAccumulator = 0.0;
			return;
		}
		if (!WireNetwork.isConnectedToRadar(world, pos)) {
			ciws.fireAccumulator = 0.0;
			return;
		}

		double centerX = pos.getX() + 0.5;
		double centerY = pos.getY() + 0.5;
		double centerZ = pos.getZ() + 0.5;

		// A missile's own radar cross-section (smaller for cruise missiles)
		// shrinks the effective detection radius used against it specifically.
		MissileEntity target = null;
		double bestDistanceSq = Double.MAX_VALUE;
		for (MissileEntity missile : MissileEntity.getActiveMissiles(serverWorld)) {
			if (missile.getFlightAge() <= LAUNCH_ACQUIRE_AGE_TICKS) {
				continue;
			}
			double effectiveRadius = ICBMBasics.CONFIG.ciwsDetectionRadius * missile.getRadarCrossSectionMultiplier();
			double distanceSq = missile.squaredDistanceTo(centerX, centerY, centerZ);
			if (distanceSq <= effectiveRadius * effectiveRadius && distanceSq < bestDistanceSq) {
				bestDistanceSq = distanceSq;
				target = missile;
			}
		}

		if (target == null) {
			ciws.fireAccumulator = 0.0;
			return;
		}

		int bursts = Math.min(MAX_BURSTS_PER_TICK, (int) Math.floor(ciws.fireAccumulator));
		for (int i = 0; i < bursts && !ciws.inventory.get(AMMO_SLOT).isEmpty(); i++) {
			ciws.fireAccumulator -= 1.0;
			ciws.inventory.get(AMMO_SLOT).decrement(1);
			ciws.fireBurst(serverWorld, centerX, centerY, centerZ, target);
		}
		ciws.markDirty();
	}

	/** One tracer round fired per burst - the rapid fireAccumulator rate is what makes it read as a "burst," not multiple rounds at once. */
	private static final int BULLETS_PER_BURST = 1;
	/** Random spread (blocks/tick, per axis) applied on a miss, so it doesn't look laser-straight to the target. */
	private static final double SPREAD = 0.35;

	private void fireBurst(ServerWorld world, double x, double y, double z, MissileEntity target) {
		Vec3d from = new Vec3d(x, y, z);
		Vec3d leadPoint = computeLeadPoint(from, target);
		double speed = ICBMBasics.CONFIG.ciwsBulletSpeed;
		double distance = leadPoint.distanceTo(from);
		int travelTicks = Math.max(1, (int) Math.ceil(distance / speed));

		Vec3d direction = leadPoint.subtract(from);
		Vec3d velocity = direction.lengthSquared() > 1.0E-6
				? direction.normalize().multiply(speed)
				: new Vec3d(0.0, speed, 0.0);

		boolean hit = world.getRandom().nextDouble() < ICBMBasics.CONFIG.ciwsAccuracy;

		for (int i = 0; i < BULLETS_PER_BURST; i++) {
			boolean lethal = hit && i == 0;
			Vec3d bulletVelocity = lethal ? velocity : velocity.add(
					(world.getRandom().nextDouble() - 0.5) * SPREAD,
					(world.getRandom().nextDouble() - 0.5) * SPREAD,
					(world.getRandom().nextDouble() - 0.5) * SPREAD);

			CiwsBulletEntity bullet = new CiwsBulletEntity(ModEntities.CIWS_BULLET, world);
			bullet.refreshPositionAndAngles(x, y, z, 0.0f, 0.0f);
			bullet.configure(bulletVelocity, travelTicks, lethal, target.getUuid());
			world.spawnEntity(bullet);
		}

		world.playSound(null, x, y, z, SoundEvents.ENTITY_GENERIC_EXPLODE, SoundCategory.HOSTILE, 1.2f, 1.8f);
	}

	/**
	 * Firing solution: where the target will be by the time a tracer traveling
	 * at {@code ICBMConfig.ciwsBulletSpeed} would reach it, assuming it holds
	 * its current velocity. A single-pass linear lead - good enough for a
	 * missile's fairly steady cruise/dive, and simpler than iterating to
	 * convergence like a real fire-control solution would.
	 */
	private static Vec3d computeLeadPoint(Vec3d from, MissileEntity target) {
		Vec3d targetPos = new Vec3d(target.getX(), target.getY(), target.getZ());
		double timeToImpact = targetPos.distanceTo(from) / ICBMBasics.CONFIG.ciwsBulletSpeed;
		return targetPos.add(target.getVelocity().multiply(timeToImpact));
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
		return stack.isOf(ModItems.CIWS_AMMO);
	}

	@Override
	public boolean canPlayerUse(PlayerEntity player) {
		return Inventory.canPlayerUse(this, player);
	}

	@Override
	public void clear() {
		this.inventory.clear();
	}

	// --------------------------------------------------------------------- gui

	@Override
	public Text getDisplayName() {
		return Text.translatable("block.icbmbasics.ciws");
	}

	@Override
	@Nullable
	public ScreenHandler createMenu(int syncId, PlayerInventory playerInventory, PlayerEntity player) {
		return new CiwsAmmoScreenHandler(syncId, playerInventory, this, this.getPos());
	}

	@Override
	public AmmoScreenData getScreenOpeningData(ServerPlayerEntity player) {
		return new AmmoScreenData(this.getPos());
	}

	// ---------------------------------------------------------------------- nbt

	@Override
	protected void writeData(WriteView view) {
		super.writeData(view);
		view.putDouble("FireAccumulator", this.fireAccumulator);
		Inventories.writeData(view, this.inventory);
	}

	@Override
	protected void readData(ReadView view) {
		super.readData(view);
		this.fireAccumulator = view.getDouble("FireAccumulator", 0.0);
		this.inventory.clear();
		Inventories.readData(view, this.inventory);
	}
}
