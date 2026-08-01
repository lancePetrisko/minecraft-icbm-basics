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
import net.minecraft.block.Block;
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
 * (the slots + player inventory) to see/refill the count.
 *
 * <p><b>Firing is chamber-based.</b> The slots are the magazine; {@link #chamber}
 * is what's actually in the tubes. A site fires one round every
 * {@code samFireCooldownTicks} (10 = 0.5s) at a different unclaimed target each
 * time, and once the chamber runs dry it spends {@code samReloadTicks}
 * (70 = 3.5s) unable to fire before restaging up to {@link #SLOT_COUNT} more
 * rounds out of the slots - partially, if that's all that's left.
 *
 * <p>Simulated against the defaults, that works out to a 6-round salvo spanning
 * 50 ticks, then a 71-tick gap to the next salvo's first shot (the 70-tick
 * reload plus the tick the refill itself consumes) - a <b>121-tick / 6.05s
 * cycle</b>, i.e. ~1 round/sec sustained, not the 2/sec the fire cooldown alone
 * suggests. Work the arithmetic out again if either config value moves.
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
	/**
	 * Rockets sitting in the tubes, ready to fire, 0..{@link #SLOT_COUNT}. This
	 * is a staging area in front of the ammo slots, not a view of them: rounds
	 * move slots -> chamber only during a reload, and it's the chamber that the
	 * rack's nose cones display.
	 */
	private int chamber;
	/** Counts down while refilling the chamber; 0 means ready. */
	private int reloadTicks;

	public SamSiteBlockEntity(BlockPos pos, BlockState state) {
		super(ModBlockEntities.SAM_SITE, pos, state);
	}

	public static void tick(World world, BlockPos pos, BlockState state, SamSiteBlockEntity site) {
		if (!(world instanceof ServerWorld serverWorld)) {
			return;
		}
		// markDirty covers every ammo change, but nothing calls it after a chunk
		// loads, so re-assert the nose cones periodically. Cheap: an int compare
		// unless the count actually differs.
		if (world.getTime() % SCAN_INTERVAL_TICKS == 0) {
			site.syncLoadedTubes();
		}

		// Timers run every tick, and the cooldown alone decides when a shot is
		// allowed. There is deliberately NO outer "only act every N ticks" gate
		// here: with SCAN_INTERVAL_TICKS == samFireCooldownTicks == 10 one would
		// happen to line up, but any shorter cooldown would then be silently
		// capped at the gate's period instead - exactly the bug that shipped on
		// CIWS and went unnoticed. The scan below is the expensive part, and it
		// only runs on ticks where the site is genuinely ready to fire.
		if (site.cooldown > 0) {
			site.cooldown--;
		}
		if (site.reloadTicks > 0) {
			site.reloadTicks--;
			if (site.reloadTicks == 0) {
				site.refillChamber();
			}
			return;
		}
		if (site.chamber <= 0) {
			// Empty chamber with rounds still in the slots starts a reload;
			// with nothing left in the slots it just sits idle, silently.
			if (!site.isEmpty()) {
				site.reloadTicks = ICBMBasics.CONFIG.samReloadTicks;
			}
			return;
		}
		if (site.cooldown > 0) {
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

		// Fire from the highest loaded tube, so the rack's nose cones empty
		// right-to-left instead of a hole appearing in the middle.
		int tube = site.chamber - 1;

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
		// The round came out of the chamber, not the slots - the slots were
		// already debited when the chamber was filled.
		site.chamber--;
		site.cooldown = ICBMBasics.CONFIG.samFireCooldownTicks;
		if (site.chamber == 0) {
			site.reloadTicks = ICBMBasics.CONFIG.samReloadTicks;
		}
		site.markDirty();
	}

	/**
	 * Moves up to a full chamber's worth of rockets out of the ammo slots and
	 * into the tubes. <b>Partial reloads are fine</b>: three rockets left means
	 * a three-round chamber and three nose cones, not a refusal to reload.
	 */
	private void refillChamber() {
		int wanted = SLOT_COUNT - this.chamber;
		for (int slot = 0; slot < this.inventory.size() && wanted > 0; slot++) {
			ItemStack stack = this.inventory.get(slot);
			int taken = Math.min(wanted, stack.getCount());
			if (taken > 0) {
				stack.decrement(taken);
				this.chamber += taken;
				wanted -= taken;
			}
		}
		this.markDirty();
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

	// ------------------------------------------------------------ loaded rounds

	/**
	 * Pushes the chamber count onto both halves' {@code LOADED} blockstate,
	 * which is what puts red nose cones in the rack's tubes - one per round
	 * actually ready to fire. Showing the chamber rather than total ammo is
	 * what makes the rack readable: cones disappear one per shot and come back
	 * together when a reload finishes, so an empty rack means "reloading", not
	 * "out of ammo".
	 */
	private void syncLoadedTubes() {
		if (this.world == null || this.world.isClient()) {
			return;
		}
		int loaded = MathHelper.clamp(this.chamber, 0, SLOT_COUNT);
		setLoaded(this.world, this.getPos(), loaded);
		setLoaded(this.world, this.getPos().up(), loaded);
	}

	/**
	 * The chambered rounds as a droppable stack, emptying the chamber. Rounds
	 * staged in the tubes have already left the ammo slots, so
	 * {@code ItemScatterer.spawn(world, pos, this)} alone would quietly delete
	 * up to {@link #SLOT_COUNT} rockets when the site is broken.
	 */
	public ItemStack removeChamberedRounds() {
		if (this.chamber <= 0) {
			return ItemStack.EMPTY;
		}
		ItemStack stack = new ItemStack(ModItems.SAM_AMMO, this.chamber);
		this.chamber = 0;
		return stack;
	}

	private static void setLoaded(World world, BlockPos pos, int loaded) {
		BlockState state = world.getBlockState(pos);
		if (state.getBlock() instanceof SamSiteBlock && state.get(SamSiteBlock.LOADED) != loaded) {
			// NOTIFY_LISTENERS, not NOTIFY_ALL - a property-only change on a block
			// that already exists must not read as a replacement, same as
			// ArmoredBlockEntity's ARMOR_DAMAGE update.
			world.setBlockState(pos, state.with(SamSiteBlock.LOADED, loaded), Block.NOTIFY_LISTENERS);
		}
	}

	@Override
	public void markDirty() {
		super.markDirty();
		// Covers every route ammo can change by: GUI, hopper, and the tick's own
		// decrement. The equality check in setLoaded keeps this from churning
		// blockstates on the many markDirty calls that don't change the count.
		this.syncLoadedTubes();
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
		view.putInt("Chamber", this.chamber);
		view.putInt("ReloadTicks", this.reloadTicks);
		Inventories.writeData(view, this.inventory);
	}

	@Override
	protected void readData(ReadView view) {
		super.readData(view);
		this.cooldown = view.getInt("Cooldown", 0);
		this.chamber = MathHelper.clamp(view.getInt("Chamber", 0), 0, SLOT_COUNT);
		this.reloadTicks = Math.max(0, view.getInt("ReloadTicks", 0));
		this.inventory.clear();
		Inventories.readData(view, this.inventory);
	}
}
