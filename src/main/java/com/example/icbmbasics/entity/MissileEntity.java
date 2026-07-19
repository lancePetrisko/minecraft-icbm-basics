package com.example.icbmbasics.entity;

import com.example.icbmbasics.ICBMBasics;
import com.example.icbmbasics.block.entity.ArmoredEntity;
import com.example.icbmbasics.config.ICBMConfig;
import com.example.icbmbasics.registry.ModItems;

import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
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
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.minecraft.world.rule.GameRules;

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * A ballistic missile. Rises vertically during a short boost phase, cruises
 * toward the target's X/Z, then dives onto the target. Explodes on arrival or
 * on any collision with terrain or entities along the way.
 *
 * <p>All flight and explosion logic is server-side; clients only receive normal
 * entity tracking updates plus locally-spawned trail particles.
 */
public class MissileEntity extends Entity implements FlyingItemEntity {
	/** Ticks of pure vertical boost after launch. */
	private static final int BOOST_TICKS = 30;
	/** Vertical speed during the boost phase (blocks/tick). */
	private static final double BOOST_SPEED = 0.9;
	/** Horizontal distance from the target at which the terminal dive begins. */
	private static final double DIVE_DISTANCE = 24.0;
	/** Collisions are ignored for this many ticks so the missile can clear the pad. */
	private static final int ARMING_TICKS = 15;
	/** Failsafe: self-destruct after 60 seconds of flight. */
	private static final int MAX_FLIGHT_TICKS = 20 * 60;
	/** Blocks tougher than this survive the extra crater carving (obsidian etc.). */
	private static final float MAX_CARVED_BLAST_RESISTANCE = 100.0f;

	/**
	 * All in-flight missiles per server world, so radar blocks can scan without
	 * iterating every entity in the world. Lazily registered on this missile's
	 * first server tick, deregistered via {@link #remove(RemovalReason)} (the
	 * method {@code discard()} itself routes through) so a chunk merely
	 * unloading doesn't look like an impact.
	 */
	private static final Map<ServerWorld, Set<MissileEntity>> ACTIVE_MISSILES = new WeakHashMap<>();

	private double targetX;
	private double targetY;
	private double targetZ;
	private boolean hasTarget;
	private boolean registeredActive;

	public MissileEntity(EntityType<? extends MissileEntity> type, World world) {
		super(type, world);
		this.noClip = false;
	}

	/** Snapshot of currently in-flight missiles in the given world. Never null. */
	public static Set<MissileEntity> getActiveMissiles(ServerWorld world) {
		return ACTIVE_MISSILES.getOrDefault(world, Set.of());
	}

	@Override
	public void remove(RemovalReason reason) {
		super.remove(reason);
		if (this.getEntityWorld() instanceof ServerWorld serverWorld) {
			Set<MissileEntity> active = ACTIVE_MISSILES.get(serverWorld);
			if (active != null) {
				active.remove(this);
			}
		}
	}

	/** Ticks since launch. Used by radar to tell a just-launched missile from one already in flight. */
	public int getFlightAge() {
		return this.age;
	}

	/**
	 * Destroys this missile mid-flight on a successful intercept (SAM/CIWS).
	 * Deliberately not {@link #explode(ServerWorld)}: a shootdown is debris, not
	 * a ground impact, so it skips the crater/armor-damage/explosion-power logic
	 * entirely and just discards with a small visual/audio flourish.
	 */
	public void destroyByInterceptor(ServerWorld world) {
		if (this.isRemoved()) {
			return;
		}
		double x = this.getX();
		double y = this.getY();
		double z = this.getZ();
		this.discard();

		world.spawnParticles(ParticleTypes.EXPLOSION, x, y, z, 1, 0.2, 0.2, 0.2, 0.0);
		world.spawnParticles(ParticleTypes.LARGE_SMOKE, x, y, z, 12, 0.4, 0.4, 0.4, 0.05);
		world.playSound(null, x, y, z, SoundEvents.ENTITY_GENERIC_EXPLODE, SoundCategory.HOSTILE, 2.0f, 1.4f);
	}

	public void setTarget(double x, double y, double z) {
		this.targetX = x;
		this.targetY = y;
		this.targetZ = z;
		this.hasTarget = true;
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
			// Local exhaust trail for smooth visuals between tracker updates.
			Vec3d v = this.getVelocity();
			world.addParticleClient(ParticleTypes.SMOKE,
					this.getX() - v.x * 0.5, this.getY() - v.y * 0.5, this.getZ() - v.z * 0.5,
					0.0, 0.0, 0.0);
			world.addParticleClient(ParticleTypes.FLAME,
					this.getX() - v.x, this.getY() - v.y, this.getZ() - v.z,
					0.0, 0.0, 0.0);
			return;
		}

		ServerWorld serverWorld = (ServerWorld) world;

		if (!this.registeredActive) {
			ACTIVE_MISSILES.computeIfAbsent(serverWorld, w -> Collections.newSetFromMap(new WeakHashMap<>())).add(this);
			this.registeredActive = true;
		}

		if (!this.hasTarget) {
			// Should not happen (launcher always sets a target), but never fly blind.
			this.discard();
			return;
		}

		this.updateVelocity();
		this.updateRotation();
		this.move(MovementType.SELF, this.getVelocity());

		// Exhaust trail broadcast to all nearby players.
		Vec3d v = this.getVelocity();
		serverWorld.spawnParticles(ParticleTypes.LARGE_SMOKE,
				this.getX() - v.x, this.getY() - v.y, this.getZ() - v.z,
				3, 0.1, 0.1, 0.1, 0.01);
		serverWorld.spawnParticles(ParticleTypes.FLAME,
				this.getX() - v.x, this.getY() - v.y, this.getZ() - v.z,
				2, 0.05, 0.05, 0.05, 0.01);

		// Engine sound "loop".
		if (this.age % 12 == 0) {
			serverWorld.playSound(null, this.getX(), this.getY(), this.getZ(),
					SoundEvents.ENTITY_FIREWORK_ROCKET_LAUNCH, SoundCategory.HOSTILE, 3.0f, 0.6f);
		}

		// ---- impact checks ------------------------------------------------
		boolean armed = this.age > ARMING_TICKS;

		if (armed && (this.horizontalCollision || this.verticalCollision || this.isOnGround())) {
			this.explode(serverWorld);
			return;
		}

		if (this.squaredDistanceTo(this.targetX, this.targetY, this.targetZ) < 4.0) {
			this.explode(serverWorld);
			return;
		}

		if (armed && !serverWorld.getOtherEntities(this, this.getBoundingBox().expand(0.25),
				entity -> entity.isAlive() && !(entity instanceof MissileEntity)).isEmpty()) {
			this.explode(serverWorld);
			return;
		}

		if (this.age > MAX_FLIGHT_TICKS) {
			this.explode(serverWorld);
		}
	}

	/**
	 * Simple three-phase "ballistic" guidance: boost straight up, cruise flat
	 * toward the target, then dive onto it. Not physically accurate, just a
	 * believable arc over a few seconds.
	 */
	private void updateVelocity() {
		double speed = ICBMBasics.CONFIG.missileSpeed;

		if (this.age < BOOST_TICKS) {
			this.setVelocity(0.0, BOOST_SPEED, 0.0);
			return;
		}

		double dx = this.targetX - this.getX();
		double dz = this.targetZ - this.getZ();
		double horizontalDistance = Math.sqrt(dx * dx + dz * dz);

		if (horizontalDistance > 0.001) {
			dx = dx / horizontalDistance;
			dz = dz / horizontalDistance;
		}

		double vy;
		if (horizontalDistance <= speed * 1.5) {
			// Directly above the target: drop straight down.
			this.setVelocity(dx * Math.min(horizontalDistance, speed * 0.25), -speed * 1.5, dz * Math.min(horizontalDistance, speed * 0.25));
			return;
		} else if (horizontalDistance < DIVE_DISTANCE) {
			// Terminal dive: point the vertical component at the target so the
			// missile arrives at (targetX, targetY, targetZ) in a smooth arc.
			double dy = this.targetY - this.getY();
			vy = MathHelper.clamp(speed * dy / horizontalDistance * 1.5, -speed * 2.0, speed);
		} else {
			// Cruise phase: gently settle onto a flat trajectory.
			vy = this.getVelocity().y * 0.85;
		}

		this.setVelocity(dx * speed, vy, dz * speed);
	}

	/**
	 * Points the entity along its current velocity so the renderer can orient
	 * the (otherwise flat) item model with the flight path - straight up
	 * during boost, laying flat on a horizontal cruise, angled into a dive.
	 * Rides on vanilla's normal yaw/pitch entity tracking; no extra networking.
	 */
	private void updateRotation() {
		Vec3d v = this.getVelocity();
		double horizontal = v.horizontalLength();
		if (horizontal > 1.0E-5 || Math.abs(v.y) > 1.0E-5) {
			this.setYaw((float) (MathHelper.atan2(v.x, v.z) * MathHelper.DEGREES_PER_RADIAN));
			this.setPitch((float) (MathHelper.atan2(v.y, horizontal) * MathHelper.DEGREES_PER_RADIAN));
		}
	}

	private void explode(ServerWorld world) {
		if (this.isRemoved()) {
			return;
		}

		ICBMConfig config = ICBMBasics.CONFIG;
		boolean mobGriefing = world.getGameRules().getValue(GameRules.DO_MOB_GRIEFING);
		boolean destroyTerrain = config.terrainDestruction && mobGriefing;

		double x = this.getX();
		double y = this.getY();
		double z = this.getZ();

		this.discard();

		// MOB source type respects the mobGriefing gamerule for block damage;
		// NONE damages entities without touching terrain at all.
		World.ExplosionSourceType sourceType = destroyTerrain
				? World.ExplosionSourceType.MOB
				: World.ExplosionSourceType.NONE;

		world.createExplosion(null, x, y, z, config.explosionPower, sourceType);

		this.damageArmoredBlocks(world, BlockPos.ofFloored(x, y, z), config.armorDamageRadius);

		// Optional extra crater beyond the vanilla explosion radius.
		if (destroyTerrain && config.destructionRadius > 0) {
			this.carveCrater(world, BlockPos.ofFloored(x, y, z), config.destructionRadius);
		}

		// Secondary shockwave: expanding particle rings around the impact point.
		for (int ring = 2; ring <= 10; ring += 2) {
			int points = ring * 8;
			for (int i = 0; i < points; i++) {
				double angle = (Math.PI * 2.0 * i) / points;
				world.spawnParticles(ParticleTypes.CLOUD,
						x + Math.cos(angle) * ring, y + 0.5, z + Math.sin(angle) * ring,
						1, 0.0, 0.05, 0.0, 0.02);
			}
		}
		world.spawnParticles(ParticleTypes.EXPLOSION_EMITTER, x, y, z, 2, 1.0, 1.0, 1.0, 0.0);

		world.playSound(null, x, y, z, SoundEvents.ENTITY_GENERIC_EXPLODE, SoundCategory.BLOCKS, 6.0f, 0.7f);
	}

	/** One hit each to every armored block/door ({@link ArmoredEntity}) within range of the impact. */
	private void damageArmoredBlocks(ServerWorld world, BlockPos center, int radius) {
		int radiusSq = radius * radius;
		BlockPos.Mutable pos = new BlockPos.Mutable();

		for (int ox = -radius; ox <= radius; ox++) {
			for (int oy = -radius; oy <= radius; oy++) {
				for (int oz = -radius; oz <= radius; oz++) {
					if (ox * ox + oy * oy + oz * oz > radiusSq) {
						continue;
					}
					pos.set(center.getX() + ox, center.getY() + oy, center.getZ() + oz);
					BlockEntity blockEntity = world.getBlockEntity(pos);
					if (blockEntity instanceof ArmoredEntity armored) {
						armored.applyMissileHit();
					}
				}
			}
		}
	}

	/**
	 * Carves a rough sphere of destroyed blocks around the impact point. Skips
	 * unbreakable blocks (bedrock) and anything with very high blast resistance
	 * (obsidian, reinforced deepslate, ...). Only runs when both the config
	 * option and the mobGriefing gamerule allow terrain destruction.
	 */
	private void carveCrater(ServerWorld world, BlockPos center, int radius) {
		int radiusSq = radius * radius;
		BlockPos.Mutable pos = new BlockPos.Mutable();

		for (int ox = -radius; ox <= radius; ox++) {
			for (int oy = -radius; oy <= radius; oy++) {
				for (int oz = -radius; oz <= radius; oz++) {
					if (ox * ox + oy * oy + oz * oz > radiusSq) {
						continue;
					}
					pos.set(center.getX() + ox, center.getY() + oy, center.getZ() + oz);
					BlockState state = world.getBlockState(pos);
					if (state.isAir()) {
						continue;
					}
					if (state.getHardness(world, pos) < 0.0f) {
						continue; // unbreakable (bedrock, end portal frame, ...)
					}
					if (state.getBlock().getBlastResistance() > MAX_CARVED_BLAST_RESISTANCE) {
						continue; // obsidian-tier blocks survive
					}
					// No drops: a crater full of item entities would melt servers.
					world.breakBlock(pos, false, this);
				}
			}
		}
	}

	@Override
	public boolean damage(ServerWorld world, DamageSource source, float amount) {
		if (this.isRemoved() || this.isInvulnerable()) {
			return false;
		}
		this.explode(world);
		return true;
	}

	@Override
	public ItemStack getStack() {
		return new ItemStack(ModItems.ICBM_MISSILE);
	}

	@Override
	protected void writeCustomData(WriteView view) {
		view.putDouble("TargetX", this.targetX);
		view.putDouble("TargetY", this.targetY);
		view.putDouble("TargetZ", this.targetZ);
		view.putBoolean("HasTarget", this.hasTarget);
		view.putInt("FlightAge", this.age);
	}

	@Override
	protected void readCustomData(ReadView view) {
		this.targetX = view.getDouble("TargetX", 0.0);
		this.targetY = view.getDouble("TargetY", 0.0);
		this.targetZ = view.getDouble("TargetZ", 0.0);
		this.hasTarget = view.getBoolean("HasTarget", false);
		this.age = view.getInt("FlightAge", 0);
	}
}
