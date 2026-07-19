package com.example.icbmbasics.block.entity;

import com.example.icbmbasics.block.MissileLauncherBlock;
import com.example.icbmbasics.entity.MissileEntity;
import com.example.icbmbasics.network.LauncherScreenData;
import com.example.icbmbasics.network.Waypoint;
import com.example.icbmbasics.registry.ModBlockEntities;
import com.example.icbmbasics.registry.ModEntities;
import com.example.icbmbasics.registry.ModItems;
import com.example.icbmbasics.screen.MissileLauncherScreenHandler;

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
import net.minecraft.world.World;

import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class MissileLauncherBlockEntity extends BlockEntity
		implements Inventory, ExtendedScreenHandlerFactory<LauncherScreenData> {

	public static final int MISSILE_SLOT = 0;
	public static final int USB_SLOT = 1;

	private final DefaultedList<ItemStack> inventory = DefaultedList.ofSize(2, ItemStack.EMPTY);
	private final List<Waypoint> waypoints = new ArrayList<>();

	private int targetX;
	private int targetY;
	private int targetZ;
	private boolean hasTarget;

	public MissileLauncherBlockEntity(BlockPos pos, BlockState state) {
		super(ModBlockEntities.MISSILE_LAUNCHER, pos, state);
	}

	// ------------------------------------------------------------------ target

	public void setTarget(int x, int y, int z) {
		this.targetX = x;
		this.targetY = y;
		this.targetZ = z;
		this.hasTarget = true;
		this.markDirty();
	}

	public boolean hasTarget() {
		return this.hasTarget;
	}

	// --------------------------------------------------------------- waypoints

	public List<Waypoint> getWaypoints() {
		return List.copyOf(this.waypoints);
	}

	/** Saves the waypoint, overwriting any existing one with the same name (case-insensitive). */
	public void saveWaypoint(Waypoint waypoint) {
		this.waypoints.removeIf(w -> w.name().equalsIgnoreCase(waypoint.name()));
		this.waypoints.add(waypoint);
		this.markDirty();
	}

	public void removeWaypoint(String name) {
		if (this.waypoints.removeIf(w -> w.name().equalsIgnoreCase(name))) {
			this.markDirty();
		}
	}

	// ------------------------------------------------------------------ launch

	/**
	 * Called by the block on a redstone rising edge. Fires only if a missile is
	 * loaded and valid target coordinates have been confirmed. Fully server-side.
	 */
	public void tryLaunch() {
		World world = this.getWorld();
		if (!(world instanceof ServerWorld serverWorld)) {
			return;
		}
		ItemStack ammo = this.inventory.get(MISSILE_SLOT);
		if (!this.hasTarget || !ammo.isOf(ModItems.ICBM_MISSILE)) {
			return;
		}

		BlockPos pos = this.getPos();
		double spawnX = pos.getX() + 0.5;
		double spawnY = pos.getY() + 1.1;
		double spawnZ = pos.getZ() + 0.5;

		Direction facing = this.getCachedState().contains(MissileLauncherBlock.FACING)
				? this.getCachedState().get(MissileLauncherBlock.FACING)
				: Direction.NORTH;

		MissileEntity missile = new MissileEntity(ModEntities.MISSILE, serverWorld);
		missile.refreshPositionAndAngles(spawnX, spawnY, spawnZ, facing.getPositiveHorizontalDegrees(), 0.0f);
		missile.setTarget(this.targetX + 0.5, this.targetY, this.targetZ + 0.5);
		serverWorld.spawnEntity(missile);

		// Consume the missile item.
		ammo.decrement(1);
		this.markDirty();

		// Launch feedback: sound + smoke burst around the pad.
		serverWorld.playSound(null, spawnX, spawnY, spawnZ,
				SoundEvents.ENTITY_FIREWORK_ROCKET_LAUNCH, SoundCategory.BLOCKS, 4.0f, 0.5f);
		serverWorld.playSound(null, spawnX, spawnY, spawnZ,
				SoundEvents.ENTITY_GENERIC_EXPLODE, SoundCategory.BLOCKS, 2.0f, 1.6f);
		serverWorld.spawnParticles(ParticleTypes.LARGE_SMOKE, spawnX, spawnY, spawnZ,
				40, 0.6, 0.3, 0.6, 0.02);
		serverWorld.spawnParticles(ParticleTypes.FLAME, spawnX, spawnY - 0.5, spawnZ,
				20, 0.4, 0.2, 0.4, 0.05);
	}

	// --------------------------------------------------------------- inventory

	@Override
	public int size() {
		return this.inventory.size();
	}

	@Override
	public boolean isEmpty() {
		return this.inventory.get(MISSILE_SLOT).isEmpty() && this.inventory.get(USB_SLOT).isEmpty();
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
		return switch (slot) {
			case MISSILE_SLOT -> stack.isOf(ModItems.ICBM_MISSILE);
			case USB_SLOT -> stack.isOf(ModItems.USB_DRIVE);
			default -> false;
		};
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
		return Text.translatable("block.icbmbasics.missile_launcher");
	}

	@Override
	@Nullable
	public ScreenHandler createMenu(int syncId, PlayerInventory playerInventory, PlayerEntity player) {
		return new MissileLauncherScreenHandler(syncId, playerInventory, this, this.getPos());
	}

	@Override
	public LauncherScreenData getScreenOpeningData(ServerPlayerEntity player) {
		return new LauncherScreenData(this.getPos(), this.targetX, this.targetY, this.targetZ, this.hasTarget,
				this.getWaypoints());
	}

	// --------------------------------------------------------------------- nbt

	@Override
	protected void writeData(WriteView view) {
		super.writeData(view);
		Inventories.writeData(view, this.inventory);
		view.putInt("TargetX", this.targetX);
		view.putInt("TargetY", this.targetY);
		view.putInt("TargetZ", this.targetZ);
		view.putBoolean("HasTarget", this.hasTarget);
		view.put("Waypoints", Waypoint.CODEC.listOf(), this.waypoints);
	}

	@Override
	protected void readData(ReadView view) {
		super.readData(view);
		this.inventory.clear();
		Inventories.readData(view, this.inventory);
		this.targetX = view.getInt("TargetX", 0);
		this.targetY = view.getInt("TargetY", 0);
		this.targetZ = view.getInt("TargetZ", 0);
		this.hasTarget = view.getBoolean("HasTarget", false);
		this.waypoints.clear();
		this.waypoints.addAll(view.read("Waypoints", Waypoint.CODEC.listOf()).orElse(List.of()));
	}
}
