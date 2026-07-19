package com.example.icbmbasics.registry;

import com.example.icbmbasics.ICBMBasics;
import com.example.icbmbasics.block.ArmoredBlock;
import com.example.icbmbasics.block.ArmoredDoorBlock;
import com.example.icbmbasics.block.MissileLauncherBlock;
import com.example.icbmbasics.block.RadarBlock;

import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.block.BlockSetType;
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

	public static final RegistryKey<Block> ARMORED_BLOCK_MK1_KEY =
			RegistryKey.of(RegistryKeys.BLOCK, ICBMBasics.id("armored_block_mk1"));
	public static final RegistryKey<Block> ARMORED_BLOCK_MK2_KEY =
			RegistryKey.of(RegistryKeys.BLOCK, ICBMBasics.id("armored_block_mk2"));
	public static final RegistryKey<Block> ARMORED_BLOCK_MK3_KEY =
			RegistryKey.of(RegistryKeys.BLOCK, ICBMBasics.id("armored_block_mk3"));

	public static final RegistryKey<Block> ARMORED_DOOR_MK1_KEY =
			RegistryKey.of(RegistryKeys.BLOCK, ICBMBasics.id("armored_door_mk1"));
	public static final RegistryKey<Block> ARMORED_DOOR_MK2_KEY =
			RegistryKey.of(RegistryKeys.BLOCK, ICBMBasics.id("armored_door_mk2"));
	public static final RegistryKey<Block> ARMORED_DOOR_MK3_KEY =
			RegistryKey.of(RegistryKeys.BLOCK, ICBMBasics.id("armored_door_mk3"));

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

	/**
	 * Three armored-block tiers, escalating hardness/blast resistance and hit
	 * count (the latter via {@code ICBMConfig.armorTierHits}). All are well
	 * above the missile crater's blast-resistance cutoff, so only
	 * {@code ArmoredBlockEntity.applyMissileHit} can actually break them.
	 */
	public static final Block ARMORED_BLOCK_MK1 = Registry.register(Registries.BLOCK, ARMORED_BLOCK_MK1_KEY,
			new ArmoredBlock(AbstractBlock.Settings.create()
					.registryKey(ARMORED_BLOCK_MK1_KEY)
					.strength(5.0f, 200.0f)
					.requiresTool()
					.sounds(BlockSoundGroup.METAL), 1));

	public static final Block ARMORED_BLOCK_MK2 = Registry.register(Registries.BLOCK, ARMORED_BLOCK_MK2_KEY,
			new ArmoredBlock(AbstractBlock.Settings.create()
					.registryKey(ARMORED_BLOCK_MK2_KEY)
					.strength(8.0f, 600.0f)
					.requiresTool()
					.sounds(BlockSoundGroup.METAL), 2));

	public static final Block ARMORED_BLOCK_MK3 = Registry.register(Registries.BLOCK, ARMORED_BLOCK_MK3_KEY,
			new ArmoredBlock(AbstractBlock.Settings.create()
					.registryKey(ARMORED_BLOCK_MK3_KEY)
					.strength(12.0f, 1200.0f)
					.requiresTool()
					.sounds(BlockSoundGroup.METAL), 3));

	/** Same tier scheme as the armored blocks, plus a numeric codelock (see {@code ArmoredDoorBlockEntity}). */
	public static final Block ARMORED_DOOR_MK1 = Registry.register(Registries.BLOCK, ARMORED_DOOR_MK1_KEY,
			new ArmoredDoorBlock(BlockSetType.IRON, AbstractBlock.Settings.create()
					.registryKey(ARMORED_DOOR_MK1_KEY)
					.strength(4.0f, 200.0f)
					.requiresTool()
					.nonOpaque()
					.sounds(BlockSoundGroup.METAL), 1));

	public static final Block ARMORED_DOOR_MK2 = Registry.register(Registries.BLOCK, ARMORED_DOOR_MK2_KEY,
			new ArmoredDoorBlock(BlockSetType.IRON, AbstractBlock.Settings.create()
					.registryKey(ARMORED_DOOR_MK2_KEY)
					.strength(6.0f, 600.0f)
					.requiresTool()
					.nonOpaque()
					.sounds(BlockSoundGroup.METAL), 2));

	public static final Block ARMORED_DOOR_MK3 = Registry.register(Registries.BLOCK, ARMORED_DOOR_MK3_KEY,
			new ArmoredDoorBlock(BlockSetType.IRON, AbstractBlock.Settings.create()
					.registryKey(ARMORED_DOOR_MK3_KEY)
					.strength(9.0f, 1200.0f)
					.requiresTool()
					.nonOpaque()
					.sounds(BlockSoundGroup.METAL), 3));

	private ModBlocks() {
	}

	public static void register() {
		// Static initializers above handle registration.
	}
}
