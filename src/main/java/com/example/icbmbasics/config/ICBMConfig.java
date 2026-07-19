package com.example.icbmbasics.config;

import com.example.icbmbasics.ICBMBasics;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Simple JSON config stored at config/icbmbasics.json.
 * Server owners can tune balance here; missing keys fall back to defaults.
 */
public class ICBMConfig {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final String FILE_NAME = ICBMBasics.MOD_ID + ".json";

	/** Explosion power. Vanilla TNT is 4.0; 10.0 is roughly a "big boom". */
	public float explosionPower = 10.0f;

	/**
	 * Extra crater-carving radius (blocks) applied on top of the vanilla explosion.
	 * Set to 0 to rely purely on the vanilla explosion. Only applies when terrain
	 * destruction is enabled AND the mobGriefing gamerule is true.
	 */
	public int destructionRadius = 8;

	/** Master switch for terrain destruction (server owners may want this off). */
	public boolean terrainDestruction = true;

	/** Horizontal cruise speed of the missile in blocks per tick. */
	public double missileSpeed = 1.1;

	/**
	 * Detection radius (blocks) per radar tier, index 0 = tier 1. Each tier is
	 * its own block/item (see {@code RadarBlock}); adding a higher tier later
	 * just means appending another entry here plus the new block/item/recipe.
	 */
	public int[] radarTierDetectionRadii = {96};

	/** How close (blocks) two independent armor zones' anchors must be to count as the same zone. */
	public int armorZoneRadius = 30;

	/** Max armored blocks/doors allowed in a single zone before placement is denied. */
	public int armorZoneMaxBlocks = 128;

	/**
	 * Missile hits absorbed per armor tier, index 0 = tier 1, before the block breaks.
	 * Shared by both armored blocks and armored doors.
	 */
	public int[] armorTierHits = {2, 5, 10};

	/** How close (blocks) a missile impact must be to damage an armored block/door. */
	public int armorDamageRadius = 6;

	/** SAM site detection/engagement radius (blocks). Ignores missiles still on the pad, same rule as radar. */
	public int samDetectionRadius = 80;

	/** Ticks between SAM interceptor launches (one at a time per site). */
	public int samFireCooldownTicks = 60;

	/** Chance (0-1) a fired SAM interceptor actually destroys its target on arrival. */
	public double samAccuracy = 0.3;

	/** Cruise speed of a SAM interceptor in blocks per tick. */
	public double samInterceptorSpeed = 1.6;

	/** CIWS detection/engagement radius (blocks). Longer than a SAM site now - trades range for accuracy. */
	public int ciwsDetectionRadius = 500;

	/**
	 * CIWS bursts per second. A tick-based cooldown tops out at 20/sec (one per
	 * tick), so this is a fractional accumulator instead (see
	 * {@code CiwsBlockEntity.fireAccumulator}) - lets it go well past that,
	 * closer to a real Phalanx's ~75/sec.
	 */
	public double ciwsRoundsPerSecond = 35.0;

	/** Chance (0-1) a single CIWS burst destroys its target. Lower than before to offset the range increase. */
	public double ciwsAccuracy = 0.3;

	/** Tracer speed (blocks/tick) used to compute the CIWS's lead - how far ahead of a moving target it aims. */
	public double ciwsBulletSpeed = 4.0;

	public static ICBMConfig load() {
		Path path = FabricLoader.getInstance().getConfigDir().resolve(FILE_NAME);
		ICBMConfig config = new ICBMConfig();

		if (Files.exists(path)) {
			try {
				config = GSON.fromJson(Files.readString(path), ICBMConfig.class);
				if (config == null) {
					config = new ICBMConfig();
				}
			} catch (IOException | com.google.gson.JsonSyntaxException e) {
				ICBMBasics.LOGGER.error("Failed to read {}, using defaults", FILE_NAME, e);
				config = new ICBMConfig();
			}
		}

		config.sanitize();
		config.save(path);
		return config;
	}

	private void sanitize() {
		explosionPower = Math.max(0.0f, Math.min(explosionPower, 50.0f));
		destructionRadius = Math.max(0, Math.min(destructionRadius, 32));
		missileSpeed = Math.max(0.2, Math.min(missileSpeed, 4.0));

		if (radarTierDetectionRadii == null || radarTierDetectionRadii.length == 0) {
			radarTierDetectionRadii = new int[]{96};
		}
		for (int i = 0; i < radarTierDetectionRadii.length; i++) {
			radarTierDetectionRadii[i] = Math.max(16, Math.min(radarTierDetectionRadii[i], 512));
		}

		armorZoneRadius = Math.max(4, Math.min(armorZoneRadius, 256));
		armorZoneMaxBlocks = Math.max(1, Math.min(armorZoneMaxBlocks, 4096));
		armorDamageRadius = Math.max(1, Math.min(armorDamageRadius, 32));

		if (armorTierHits == null || armorTierHits.length == 0) {
			armorTierHits = new int[]{2, 5, 10};
		}
		for (int i = 0; i < armorTierHits.length; i++) {
			armorTierHits[i] = Math.max(1, Math.min(armorTierHits[i], 200));
		}

		samDetectionRadius = Math.max(16, Math.min(samDetectionRadius, 512));
		samFireCooldownTicks = Math.max(1, Math.min(samFireCooldownTicks, 2000));
		samAccuracy = Math.max(0.0, Math.min(samAccuracy, 1.0));
		samInterceptorSpeed = Math.max(0.2, Math.min(samInterceptorSpeed, 4.0));

		ciwsDetectionRadius = Math.max(8, Math.min(ciwsDetectionRadius, 500));
		ciwsRoundsPerSecond = Math.max(0.5, Math.min(ciwsRoundsPerSecond, 200.0));
		ciwsAccuracy = Math.max(0.0, Math.min(ciwsAccuracy, 1.0));
		ciwsBulletSpeed = Math.max(0.5, Math.min(ciwsBulletSpeed, 20.0));
	}

	private void save(Path path) {
		try {
			Files.createDirectories(path.getParent());
			Files.writeString(path, GSON.toJson(this));
		} catch (IOException e) {
			ICBMBasics.LOGGER.error("Failed to write {}", FILE_NAME, e);
		}
	}
}
