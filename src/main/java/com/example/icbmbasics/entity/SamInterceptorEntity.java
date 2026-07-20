package com.example.icbmbasics.entity;

import com.example.icbmbasics.ICBMBasics;
import com.example.icbmbasics.block.entity.SamSiteBlockEntity;
import com.example.icbmbasics.registry.ModItems;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.FlyingItemEntity;
import net.minecraft.entity.MovementType;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.item.ItemStack;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.storage.ReadView;
import net.minecraft.storage.WriteView;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import java.util.UUID;

/**
 * A SAM site's interceptor rocket. Homes in on a {@link MissileEntity} target
 * by UUID (resolved fresh each tick, not held as a direct reference, so a
 * target that resolves/despawns is handled cleanly) and rolls the SAM
 * accuracy config value once it gets close enough to matter.
 */
public class SamInterceptorEntity extends Entity implements FlyingItemEntity {
	private static final double HIT_DISTANCE_SQ = 3.0 * 3.0;
	private static final int MAX_FLIGHT_TICKS = 20 * 20;

	private UUID targetId;

	public SamInterceptorEntity(EntityType<? extends SamInterceptorEntity> type, World world) {
		super(type, world);
		this.noClip = true;
	}

	public void setTarget(MissileEntity target) {
		this.targetId = target.getUuid();
	}

	/** The missile UUID this interceptor is homing on - used by {@code MissileEntity} to notice it's being chased and juke. */
	public UUID getTargetId() {
		return this.targetId;
	}

	/**
	 * Releases this interceptor's SAM-site claim on its target whenever it
	 * stops existing, however that happens (hit, miss, lost target, timeout).
	 * A single override here beats releasing at every {@code discard()} call
	 * site in {@link #tick()} - same reasoning as {@code MissileEntity}'s own
	 * override of this method for its active-missile registry.
	 */
	@Override
	public void remove(RemovalReason reason) {
		super.remove(reason);
		if (this.targetId != null && this.getEntityWorld() instanceof ServerWorld serverWorld) {
			SamSiteBlockEntity.releaseClaim(serverWorld, this.targetId);
		}
	}

	@Override
	protected void initDataTracker(DataTracker.Builder builder) {
		// No synced data needed; position/velocity use vanilla entity tracking.
	}

	@Override
	public void tick() {
		super.tick();
		World world = this.getEntityWorld();

		if (world.isClient()) {
			Vec3d v = this.getVelocity();
			world.addParticleClient(ParticleTypes.SMOKE,
					this.getX() - v.x * 0.5, this.getY() - v.y * 0.5, this.getZ() - v.z * 0.5,
					0.0, 0.0, 0.0);
			return;
		}

		ServerWorld serverWorld = (ServerWorld) world;

		if (this.targetId == null || this.age > MAX_FLIGHT_TICKS) {
			this.discard();
			return;
		}

		MissileEntity target = findTarget(serverWorld, this.targetId);
		if (target == null) {
			// Target already resolved (impacted/intercepted) - nothing left to chase.
			this.discard();
			return;
		}

		double dx = target.getX() - this.getX();
		double dy = target.getY() - this.getY();
		double dz = target.getZ() - this.getZ();
		double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
		double speed = ICBMBasics.CONFIG.samInterceptorSpeed;

		if (distance > 0.001) {
			this.setVelocity(dx / distance * speed, dy / distance * speed, dz / distance * speed);
			this.setYaw((float) (MathHelper.atan2(dx, dz) * MathHelper.DEGREES_PER_RADIAN));
			this.setPitch((float) (MathHelper.atan2(dy, Math.sqrt(dx * dx + dz * dz)) * MathHelper.DEGREES_PER_RADIAN));
		}
		this.move(MovementType.SELF, this.getVelocity());

		Vec3d v = this.getVelocity();
		serverWorld.spawnParticles(ParticleTypes.SMOKE,
				this.getX() - v.x, this.getY() - v.y, this.getZ() - v.z,
				2, 0.05, 0.05, 0.05, 0.01);

		if (this.squaredDistanceTo(target) < HIT_DISTANCE_SQ) {
			if (serverWorld.getRandom().nextDouble() < ICBMBasics.CONFIG.samAccuracy) {
				target.destroyByInterceptor(serverWorld);
			} else {
				serverWorld.spawnParticles(ParticleTypes.CLOUD, this.getX(), this.getY(), this.getZ(),
						6, 0.3, 0.3, 0.3, 0.02);
				serverWorld.playSound(null, this.getX(), this.getY(), this.getZ(),
						SoundEvents.ENTITY_FIREWORK_ROCKET_TWINKLE, SoundCategory.HOSTILE, 1.5f, 1.2f);
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
		return new ItemStack(ModItems.ICBM_MISSILE);
	}

	@Override
	protected void writeCustomData(WriteView view) {
		if (this.targetId != null) {
			view.putString("Target", this.targetId.toString());
		}
	}

	@Override
	protected void readCustomData(ReadView view) {
		String target = view.getString("Target", "");
		if (!target.isEmpty()) {
			this.targetId = UUID.fromString(target);
		}
	}
}
