package com.example.icbmbasics.registry;

import com.example.icbmbasics.ICBMBasics;
import com.example.icbmbasics.block.MissileLauncherBlock;

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

	public static final Block MISSILE_LAUNCHER = Registry.register(Registries.BLOCK, MISSILE_LAUNCHER_KEY,
			new MissileLauncherBlock(AbstractBlock.Settings.create()
					.registryKey(MISSILE_LAUNCHER_KEY)
					.strength(3.5f, 8.0f)
					.requiresTool()
					.sounds(BlockSoundGroup.METAL)));

	private ModBlocks() {
	}

	public static void register() {
		// Static initializers above handle registration.
	}
}
