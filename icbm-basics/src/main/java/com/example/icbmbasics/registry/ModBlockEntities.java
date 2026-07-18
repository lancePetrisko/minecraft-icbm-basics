package com.example.icbmbasics.registry;

import com.example.icbmbasics.ICBMBasics;
import com.example.icbmbasics.block.entity.MissileLauncherBlockEntity;

import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;

import java.util.Set;

public final class ModBlockEntities {
	public static final BlockEntityType<MissileLauncherBlockEntity> MISSILE_LAUNCHER =
			Registry.register(Registries.BLOCK_ENTITY_TYPE, ICBMBasics.id("missile_launcher"),
					new BlockEntityType<>(MissileLauncherBlockEntity::new, Set.of(ModBlocks.MISSILE_LAUNCHER)));

	private ModBlockEntities() {
	}

	public static void register() {
	}
}
