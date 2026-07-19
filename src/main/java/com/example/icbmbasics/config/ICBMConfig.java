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
