package com.example.icbmbasics.registry;

import com.example.icbmbasics.ICBMBasics;
import com.example.icbmbasics.block.entity.MissileLauncherBlockEntity;

import net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;

public final class ModBlockEntities {
	public static final BlockEntityType<MissileLauncherBlockEntity> MISSILE_LAUNCHER =
			Registry.register(Registries.BLOCK_ENTITY_TYPE, ICBMBasics.id("missile_launcher"),
					FabricBlockEntityTypeBuilder.create(MissileLauncherBlockEntity::new, ModBlocks.MISSILE_LAUNCHER).build());

	private ModBlockEntities() {
	}

	public static void register() {
	}
}
