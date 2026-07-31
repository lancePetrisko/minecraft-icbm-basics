package com.example.icbmbasics.block.entity;

import com.example.icbmbasics.ICBMBasics;
import com.example.icbmbasics.block.SamSiteBlock;
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
import net.minecraft.particle.ParticleTypes;
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
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
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
 * <p>{@link #SLOT_COUNT}-slot {@link Inventory} for
 * {@code ICBM_BASICS.SAM_AMMO} - hopper fed, or right-click opens a small GUI
 * (the slots + player inventory) to see/refill the count. <b>One slot per
 * launch tube</b>: the site cycles tubes round-robin ({@link #nextTube}) and a
 * tube whose own slot is empty is skipped, so the model's six tubes and the
 * GUI's six slots are the same six things.
 *
 * <p>Only fires while {@link WireNetwork#isConnectedToRadar} finds a path to a
 * radar - direct adjacency or a chain of {@code WIRE} blocks. Unconnected
 * sites just sit idle regardless of ammo/cooldown. The check runs against
 * <b>both</b> halves of the two-tall block, since wire butted against the tube
 * rack is every bit as much a connection as wire against the base.
 */
public class SamSiteBlockEntity extends BlockEntity
		implements Inventory, ExtendedScreenHandlerFactory<AmmoScreenData> {
	private static final int SCAN_INTERVAL_TICKS = 10;
	private static final int LAUNCH_ACQUIRE_AGE_TICKS = 20;
	public static final int AMMO_SLOT = 0;
	/** One ammo slot per launch tube on the model's rack. */
	public static final int SLOT_COUNT = 6;

	/**
	 * Rack geometry, in the model's own 16-units-per-block space and its own
	 * frame (authored facing north, so "forward" is -Z). These are the same
	 * numbers {@code models/block/sam_site_upper.json} is built from - the
	 * muzzle positions below are derived by applying the model's element
	 * rotation to them, rather than by measuring the rendered result, so the
	 * rockets can't drift away from the tubes they're drawn leaving.
	 */
	private static final double TUBE_TILT_DEGREES = 22.5;
	private static final double[] TUBE_COLUMN_X = {4.0, 8.0, 12.0};
	private static final double[] TUBE_ROW_Z = {6.0, 10.0};
	/** Assembly-space y of the muzzle rims' top face, and the pivot the tilt turns about. */
	private static final double MUZZLE_TOP_Y = 31.0;
	private static final double PIVOT_Y = 20.0;
	private static final double PIVOT_Z = 8.0;
	private static final double MODEL_CENTER = 8.0;
	private static final double UNITS_PER_BLOCK = 16.0;

	private static final Map<ServerWorld, Set<UUID>> CLAIMED_TARGETS = new WeakHashMap<>();

	private final DefaultedList<ItemStack> inventory = DefaultedList.ofSize(SLOT_COUNT, ItemStack.EMPTY);
	private int cooldown;
	/** Which tube fires next, so a full rack empties evenly instead of always launching from tube 0. */
	private int nextTube;

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
		if (site.isEmpty()) {
			return;
		}
		// Wire may butt against either half of the two-tall block; the block
		// entity only lives on the lower one, so check the rack's position too.
		if (!WireNetwork.isConnectedToRadar(world, pos) && !WireNetwork.isConnectedToRadar(world, pos.up())) {
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

		int tube = site.selectLoadedTube();
		if (tube < 0) {
			return;
		}

		claim(serverWorld, target.getUuid());

		// Sites placed before the block gained a FACING read back as the default
		// state (north), which is exactly the orientation the model is authored in.
		Direction facing = state.get(SamSiteBlock.FACING);
		Vec3d muzzle = muzzlePosition(pos, facing, tube);
		Vec3d launchDirection = launchDirection(facing);

		SamInterceptorEntity interceptor = new SamInterceptorEntity(ModEntities.SAM_INTERCEPTOR, serverWorld);
		interceptor.refreshPositionAndAngles(muzzle.x, muzzle.y, muzzle.z,
				(float) (MathHelper.atan2(launchDirection.x, launchDirection.z) * MathHelper.DEGREES_PER_RADIAN),
				(float) (MathHelper.atan2(launchDirection.y,
						Math.sqrt(launchDirection.x * launchDirection.x + launchDirection.z * launchDirection.z))
						* MathHelper.DEGREES_PER_RADIAN));
		interceptor.setTarget(target);
		interceptor.boostFrom(launchDirection);
		serverWorld.spawnEntity(interceptor);

		serverWorld.spawnParticles(ParticleTypes.LARGE_SMOKE, muzzle.x, muzzle.y, muzzle.z, 8, 0.1, 0.1, 0.1, 0.02);
		serverWorld.playSound(null, pos, SoundEvents.ENTITY_FIREWORK_ROCKET_LAUNCH, SoundCategory.HOSTILE, 3.0f, 1.3f);
		site.inventory.get(tube).decrement(1);
		site.nextTube = (tube + 1) % SLOT_COUNT;
		site.cooldown = ICBMBasics.CONFIG.samFireCooldownTicks;
		site.markDirty();
	}

	/**
	 * Next tube in the round-robin whose own ammo slot still has a rocket in
	 * it, or -1 if the whole rack is dry.
	 */
	private int selectLoadedTube() {
		for (int offset = 0; offset < SLOT_COUNT; offset++) {
			int tube = (this.nextTube + offset) % SLOT_COUNT;
			if (!this.inventory.get(tube).isEmpty()) {
				return tube;
			}
		}
		return -1;
	}

	/**
	 * Where the given tube's muzzle actually sits in the world, so a rocket
	 * leaves the tube it was drawn from rather than the block's center.
	 *
	 * <p>Applies the rack model's own element rotation - {@code -TUBE_TILT_DEGREES}
	 * about X, at {@code (PIVOT_Y, PIVOT_Z)} - to the tube's authored position,
	 * then maps the result out of the model's north-facing frame into the
	 * world using the block's {@code FACING}. The back row therefore comes out
	 * both higher and less far forward than the front row, exactly as the tilt
	 * makes it look.
	 */
	private static Vec3d muzzlePosition(BlockPos pos, Direction facing, int tube) {
		int column = tube % TUBE_COLUMN_X.length;
		int row = tube / TUBE_COLUMN_X.length;

		double tilt = Math.toRadians(TUBE_TILT_DEGREES);
		double dy = MUZZLE_TOP_Y - PIVOT_Y;
		double dz = TUBE_ROW_Z[row] - PIVOT_Z;
		// Rotation about X, negated: the model tilts toward -Z, its own front.
		double rotatedY = PIVOT_Y + dy * Math.cos(tilt) + dz * Math.sin(tilt);
		double rotatedZ = PIVOT_Z - dy * Math.sin(tilt) + dz * Math.cos(tilt);

		double across = (TUBE_COLUMN_X[column] - MODEL_CENTER) / UNITS_PER_BLOCK;
		double forward = (MODEL_CENTER - rotatedZ) / UNITS_PER_BLOCK;
		// Model +X is east when the block faces north, so "right" is clockwise.
		Direction right = facing.rotateYClockwise();

		return new Vec3d(
				pos.getX() + 0.5 + right.getOffsetX() * across + facing.getOffsetX() * forward,
				pos.getY() + rotatedY / UNITS_PER_BLOCK,
				pos.getZ() + 0.5 + right.getOffsetZ() * across + facing.getOffsetZ() * forward);
	}

	/** Straight up, canted {@link #TUBE_TILT_DEGREES} toward {@code facing} - the direction the tubes point. */
	private static Vec3d launchDirection(Direction facing) {
		double tilt = Math.toRadians(TUBE_TILT_DEGREES);
		double horizontal = Math.sin(tilt);
		return new Vec3d(
				facing.getOffsetX() * horizontal,
				Math.cos(tilt),
				facing.getOffsetZ() * horizontal);
	}

	// --------------------------------------------------------------- inventory

	@Override
	public int size() {
		return this.inventory.size();
	}

	@Override
	public boolean isEmpty() {
		for (ItemStack stack : this.inventory) {
			if (!stack.isEmpty()) {
				return false;
			}
		}
		return true;
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
		view.putInt("NextTube", this.nextTube);
		Inventories.writeData(view, this.inventory);
	}

	@Override
	protected void readData(ReadView view) {
		super.readData(view);
		this.cooldown = view.getInt("Cooldown", 0);
		this.nextTube = MathHelper.clamp(view.getInt("NextTube", 0), 0, SLOT_COUNT - 1);
		this.inventory.clear();
		Inventories.readData(view, this.inventory);
	}
}
