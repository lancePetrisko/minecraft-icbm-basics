package com.example.icbmbasics.block.entity;

import com.example.icbmbasics.ICBMBasics;
import com.example.icbmbasics.entity.MissileEntity;
import com.example.icbmbasics.entity.SamInterceptorEntity;
import com.example.icbmbasics.registry.ModBlockEntities;
import com.example.icbmbasics.registry.ModEntities;

import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.storage.ReadView;
import net.minecraft.storage.WriteView;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/**
 * Always-on ground-to-air defense: every {@link #SCAN_INTERVAL_TICKS} it
 * looks for the nearest missile in range that isn't still on its own pad
 * (same age-based "outgoing" rule radar uses) and, once its cooldown is
 * ready, launches a homing {@link SamInterceptorEntity} at it. No GUI - this
 * is a fire-and-forget defensive structure, unlike the radar it shares
 * detection logic with.
 */
public class SamSiteBlockEntity extends BlockEntity {
	private static final int SCAN_INTERVAL_TICKS = 10;
	private static final int LAUNCH_ACQUIRE_AGE_TICKS = 20;

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

		double radiusSq = (double) ICBMBasics.CONFIG.samDetectionRadius * ICBMBasics.CONFIG.samDetectionRadius;
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

		SamInterceptorEntity interceptor = new SamInterceptorEntity(ModEntities.SAM_INTERCEPTOR, serverWorld);
		interceptor.refreshPositionAndAngles(centerX, centerY + 1.0, centerZ, 0.0f, 0.0f);
		interceptor.setTarget(target);
		serverWorld.spawnEntity(interceptor);

		serverWorld.playSound(null, pos, SoundEvents.ENTITY_FIREWORK_ROCKET_LAUNCH, SoundCategory.HOSTILE, 3.0f, 1.3f);
		site.cooldown = ICBMBasics.CONFIG.samFireCooldownTicks;
		site.markDirty();
	}

	@Override
	protected void writeData(WriteView view) {
		super.writeData(view);
		view.putInt("Cooldown", this.cooldown);
	}

	@Override
	protected void readData(ReadView view) {
		super.readData(view);
		this.cooldown = view.getInt("Cooldown", 0);
	}
}
