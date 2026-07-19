package com.example.icbmbasics.registry;

import com.example.icbmbasics.ICBMBasics;
import com.example.icbmbasics.block.entity.ArmoredBlockEntity;
import com.example.icbmbasics.block.entity.ArmoredDoorBlockEntity;
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

	/** Shared by every armored-block tier. */
	public static final BlockEntityType<ArmoredBlockEntity> ARMORED_BLOCK =
			Registry.register(Registries.BLOCK_ENTITY_TYPE, ICBMBasics.id("armored_block"),
					FabricBlockEntityTypeBuilder.create(ArmoredBlockEntity::new,
							ModBlocks.ARMORED_BLOCK_MK1, ModBlocks.ARMORED_BLOCK_MK2, ModBlocks.ARMORED_BLOCK_MK3).build());

	/** Shared by every armored-door tier. */
	public static final BlockEntityType<ArmoredDoorBlockEntity> ARMORED_DOOR =
			Registry.register(Registries.BLOCK_ENTITY_TYPE, ICBMBasics.id("armored_door"),
					FabricBlockEntityTypeBuilder.create(ArmoredDoorBlockEntity::new,
							ModBlocks.ARMORED_DOOR_MK1, ModBlocks.ARMORED_DOOR_MK2, ModBlocks.ARMORED_DOOR_MK3).build());

	private ModBlockEntities() {
	}

	public static void register() {
	}
}
