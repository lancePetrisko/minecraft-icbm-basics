package com.example.icbmbasics.registry;

import com.example.icbmbasics.ICBMBasics;
import com.example.icbmbasics.block.MissileLauncherBlock;
import com.example.icbmbasics.block.RadarBlock;

import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.sound.BlockSoundGroup;

public final class ModBlocks {
	public static final RegistryKey<Block> MISSILE_LAUNCHER_KEY =
			RegistryKey.of(RegistryKeys.BLOCK, ICBMBasics.id("missile_launcher"));
	public static final RegistryKey<Block> RADAR_MK1_KEY =
			RegistryKey.of(RegistryKeys.BLOCK, ICBMBasics.id("radar_mk1"));

	public static final Block MISSILE_LAUNCHER = Registry.register(Registries.BLOCK, MISSILE_LAUNCHER_KEY,
			new MissileLauncherBlock(AbstractBlock.Settings.create()
					.registryKey(MISSILE_LAUNCHER_KEY)
					.strength(3.5f, 8.0f)
					.requiresTool()
					.sounds(BlockSoundGroup.METAL)));

	/**
	 * Tier 1 radar. Higher tiers (better detection radius/accuracy per the
	 * TODO roadmap) are meant to be added the same way: another {@code RadarBlock}
	 * instance with a higher tier number, its own item/recipe, and another
	 * entry in {@code ICBMConfig.radarTierDetectionRadii}.
	 */
	public static final Block RADAR_MK1 = Registry.register(Registries.BLOCK, RADAR_MK1_KEY,
			new RadarBlock(AbstractBlock.Settings.create()
					.registryKey(RADAR_MK1_KEY)
					.strength(3.5f, 8.0f)
					.requiresTool()
					.sounds(BlockSoundGroup.METAL), 1));

	private ModBlocks() {
	}

	public static void register() {
		// Static initializers above handle registration.
	}
}
