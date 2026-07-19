package com.example.icbmbasics.registry;

import com.example.icbmbasics.ICBMBasics;
import com.example.icbmbasics.block.entity.MissileLauncherBlockEntity;
import com.example.icbmbasics.block.entity.RadarBlockEntity;

import net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;

public final class ModBlockEntities {
	public static final BlockEntityType<MissileLauncherBlockEntity> MISSILE_LAUNCHER =
			Registry.register(Registries.BLOCK_ENTITY_TYPE, ICBMBasics.id("missile_launcher"),
					FabricBlockEntityTypeBuilder.create(MissileLauncherBlockEntity::new, ModBlocks.MISSILE_LAUNCHER).build());

	/** Shared by every radar tier - add new tier blocks to this vararg list as they're introduced. */
	public static final BlockEntityType<RadarBlockEntity> RADAR =
			Registry.register(Registries.BLOCK_ENTITY_TYPE, ICBMBasics.id("radar"),
					FabricBlockEntityTypeBuilder.create(RadarBlockEntity::new, ModBlocks.RADAR_MK1).build());

	private ModBlockEntities() {
	}

	public static void register() {
	}
}
