package com.example.icbmbasics.entity;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.FlyingItemEntity;
import net.minecraft.entity.MovementType;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.storage.ReadView;
import net.minecraft.storage.WriteView;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import java.util.UUID;

/**
 * Purely cosmetic tracer round fired by a CIWS burst - renders as a small
 * flying gray-concrete "block" (via {@code MissileEntityRenderer}, same as
 * the missile/SAM interceptor) so a stream of them reads as tracer fire, the
 * way a real Phalanx's rounds do. Flies a straight, ballistic line (no
 * gravity, no per-tick homing) toward wherever {@code CiwsBlockEntity}
 * already computed its lead point to be - the round doesn't decide whether it
 * hits, it just visually arrives on schedule.
 *
 * <p>Only the round CIWS marked {@code lethal} (the burst's accuracy roll
 * already happened before any of them spawned) can actually destroy the
 * target, and only once it "arrives" ({@link #travelTicks} elapsed) - so the
 * explosion/particle payoff lines up with the tracer reaching the target
 * instead of firing instantly at the muzzle.
 */
public class CiwsBulletEntity extends Entity implements FlyingItemEntity {
	private static final int MAX_LIFE_TICKS = 60;

	private int travelTicks;
	private boolean lethal;
	private UUID targetId;

	public CiwsBulletEntity(EntityType<? extends CiwsBulletEntity> type, World world) {
		super(type, world);
		this.noClip = true;
	}

	/** Called right after spawning, before the first tick. */
	public void configure(Vec3d velocity, int travelTicks, boolean lethal, UUID targetId) {
		this.setVelocity(velocity);
		this.travelTicks = travelTicks;
		this.lethal = lethal;
		this.targetId = targetId;

		double horizontal = velocity.horizontalLength();
		if (horizontal > 1.0E-5 || Math.abs(velocity.y) > 1.0E-5) {
			this.setYaw((float) (MathHelper.atan2(velocity.x, velocity.z) * MathHelper.DEGREES_PER_RADIAN));
			this.setPitch((float) (MathHelper.atan2(velocity.y, horizontal) * MathHelper.DEGREES_PER_RADIAN));
		}
	}

	@Override
	protected void initDataTracker(DataTracker.Builder builder) {
		// No synced data needed; position/velocity use vanilla entity tracking.
	}

	@Override
	public void tick() {
		super.tick();
		this.move(MovementType.SELF, this.getVelocity());

		if (!(this.getEntityWorld() instanceof ServerWorld serverWorld)) {
			return;
		}

		if (this.age >= this.travelTicks || this.age >= MAX_LIFE_TICKS) {
			if (this.lethal && this.targetId != null) {
				MissileEntity target = findTarget(serverWorld, this.targetId);
				if (target != null) {
					target.destroyByInterceptor(serverWorld);
				}
			}
			this.discard();
		}
	}

	private static MissileEntity findTarget(ServerWorld world, UUID id) {
		for (MissileEntity missile : MissileEntity.getActiveMissiles(world)) {
			if (missile.getUuid().equals(id)) {
				return missile;
			}
		}
		return null;
	}

	@Override
	public boolean damage(ServerWorld world, DamageSource source, float amount) {
		return false;
	}

	@Override
	public ItemStack getStack() {
		return new ItemStack(Items.GRAY_CONCRETE);
	}

	@Override
	protected void writeCustomData(WriteView view) {
		// Purely cosmetic and short-lived; nothing worth persisting across a save.
	}

	@Override
	protected void readCustomData(ReadView view) {
	}
}
