package com.example.icbmbasics.block.entity;

import com.example.icbmbasics.ICBMBasics;
import com.example.icbmbasics.entity.MissileEntity;
import com.example.icbmbasics.network.AmmoScreenData;
import com.example.icbmbasics.registry.ModBlockEntities;
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
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import org.jetbrains.annotations.Nullable;

/**
 * A close-in weapon system: short range, fast reload, hitscan "burst" instead
 * of a projectile entity. Shares the radar/SAM's age-based rule for ignoring
 * missiles still on their own pad. Rolls {@code ICBMConfig.ciwsAccuracy} once
 * per burst rather than modeling individual bullets.
 *
 * <p>Single-slot {@link Inventory} for {@code ICBM_BASICS.CIWS_AMMO} - hopper
 * fed, or right-click opens a small GUI (just the slot + player inventory) to
 * see/refill the count.
 */
public class CiwsBlockEntity extends BlockEntity
		implements Inventory, ExtendedScreenHandlerFactory<AmmoScreenData> {
	private static final int SCAN_INTERVAL_TICKS = 5;
	private static final int LAUNCH_ACQUIRE_AGE_TICKS = 20;
	public static final int AMMO_SLOT = 0;

	private final DefaultedList<ItemStack> inventory = DefaultedList.ofSize(1, ItemStack.EMPTY);
	private int cooldown;

	public CiwsBlockEntity(BlockPos pos, BlockState state) {
		super(ModBlockEntities.CIWS, pos, state);
	}

	public static void tick(World world, BlockPos pos, BlockState state, CiwsBlockEntity ciws) {
		if (!(world instanceof ServerWorld serverWorld)) {
			return;
		}
		if (ciws.cooldown > 0) {
			ciws.cooldown--;
		}
		if (world.getTime() % SCAN_INTERVAL_TICKS != 0 || ciws.cooldown > 0) {
			return;
		}
		if (ciws.inventory.get(AMMO_SLOT).isEmpty()) {
			return;
		}

		double radiusSq = (double) ICBMBasics.CONFIG.ciwsDetectionRadius * ICBMBasics.CONFIG.ciwsDetectionRadius;
		double centerX = pos.getX() + 0.5;
		double centerY = pos.getY() + 0.5;
		double centerZ = pos.getZ() + 0.5;

		MissileEntity target = null;
		double bestDistanceSq = Double.MAX_VALUE;
		for (MissileEntity missile : MissileEntity.getActiveMissiles(serverWorld)) {
			if (missile.getFlightAge() <= LAUNCH_ACQUIRE_AGE_TICKS) {
				continue;
			}
			double distanceSq = missile.squaredDistanceTo(centerX, centerY, centerZ);
			if (distanceSq <= radiusSq && distanceSq < bestDistanceSq) {
				bestDistanceSq = distanceSq;
				target = missile;
			}
		}

		if (target == null) {
			return;
		}

		ciws.cooldown = ICBMBasics.CONFIG.ciwsFireCooldownTicks;
		ciws.inventory.get(AMMO_SLOT).decrement(1);
		ciws.fireBurst(serverWorld, centerX, centerY, centerZ, target);
		ciws.markDirty();
	}

	private void fireBurst(ServerWorld world, double x, double y, double z, MissileEntity target) {
		Vec3d from = new Vec3d(x, y, z);
		Vec3d to = new Vec3d(target.getX(), target.getY(), target.getZ());
		Vec3d step = to.subtract(from).multiply(1.0 / 12.0);
		for (int i = 1; i <= 12; i++) {
			Vec3d p = from.add(step.multiply(i));
			world.spawnParticles(ParticleTypes.CRIT, p.x, p.y, p.z, 1, 0.0, 0.0, 0.0, 0.0);
		}
		world.playSound(null, x, y, z, SoundEvents.ENTITY_GENERIC_EXPLODE, SoundCategory.HOSTILE, 1.2f, 1.8f);

		if (world.getRandom().nextDouble() < ICBMBasics.CONFIG.ciwsAccuracy) {
			target.destroyByInterceptor(world);
		}
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
