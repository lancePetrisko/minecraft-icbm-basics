package com.example.icbmbasics.registry;

import com.example.icbmbasics.ICBMBasics;
import com.example.icbmbasics.item.ArmorToolItem;
import com.example.icbmbasics.item.UsbDriveItem;

import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.TallBlockItem;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;

public final class ModItems {
	public static final RegistryKey<Item> ICBM_MISSILE_KEY =
			RegistryKey.of(RegistryKeys.ITEM, ICBMBasics.id("icbm_missile"));
	public static final RegistryKey<Item> MISSILE_LAUNCHER_KEY =
			RegistryKey.of(RegistryKeys.ITEM, ICBMBasics.id("missile_launcher"));
	public static final RegistryKey<Item> USB_DRIVE_KEY =
			RegistryKey.of(RegistryKeys.ITEM, ICBMBasics.id("usb_drive"));
	public static final RegistryKey<Item> RADAR_MK1_KEY =
			RegistryKey.of(RegistryKeys.ITEM, ICBMBasics.id("radar_mk1"));
	public static final RegistryKey<Item> ARMORED_BLOCK_MK1_KEY =
			RegistryKey.of(RegistryKeys.ITEM, ICBMBasics.id("armored_block_mk1"));
	public static final RegistryKey<Item> ARMORED_BLOCK_MK2_KEY =
			RegistryKey.of(RegistryKeys.ITEM, ICBMBasics.id("armored_block_mk2"));
	public static final RegistryKey<Item> ARMORED_BLOCK_MK3_KEY =
			RegistryKey.of(RegistryKeys.ITEM, ICBMBasics.id("armored_block_mk3"));
	public static final RegistryKey<Item> ARMORED_DOOR_MK1_KEY =
			RegistryKey.of(RegistryKeys.ITEM, ICBMBasics.id("armored_door_mk1"));
	public static final RegistryKey<Item> ARMORED_DOOR_MK2_KEY =
			RegistryKey.of(RegistryKeys.ITEM, ICBMBasics.id("armored_door_mk2"));
	public static final RegistryKey<Item> ARMORED_DOOR_MK3_KEY =
			RegistryKey.of(RegistryKeys.ITEM, ICBMBasics.id("armored_door_mk3"));
	public static final RegistryKey<Item> ARMOR_TOOL_KEY =
			RegistryKey.of(RegistryKeys.ITEM, ICBMBasics.id("armor_tool"));
	public static final RegistryKey<Item> SAM_SITE_KEY =
			RegistryKey.of(RegistryKeys.ITEM, ICBMBasics.id("sam_site"));
	public static final RegistryKey<Item> CIWS_KEY =
			RegistryKey.of(RegistryKeys.ITEM, ICBMBasics.id("ciws"));
	public static final RegistryKey<Item> SAM_AMMO_KEY =
			RegistryKey.of(RegistryKeys.ITEM, ICBMBasics.id("sam_ammo"));
	public static final RegistryKey<Item> CIWS_AMMO_KEY =
			RegistryKey.of(RegistryKeys.ITEM, ICBMBasics.id("ciws_ammo"));
	public static final RegistryKey<Item> WIRE_KEY =
			RegistryKey.of(RegistryKeys.ITEM, ICBMBasics.id("wire"));

	/**
	 * The missile is deliberately a plain {@link Item}: it has no use/placement
	 * behavior on its own and only functions as ammo inside the launcher.
	 */
	public static final Item ICBM_MISSILE = Registry.register(Registries.ITEM, ICBM_MISSILE_KEY,
			new Item(new Item.Settings().registryKey(ICBM_MISSILE_KEY).maxCount(16)));

	public static final Item MISSILE_LAUNCHER = Registry.register(Registries.ITEM, MISSILE_LAUNCHER_KEY,
			new BlockItem(ModBlocks.MISSILE_LAUNCHER, new Item.Settings()
					.registryKey(MISSILE_LAUNCHER_KEY)
					.useBlockPrefixedTranslationKey()));

	public static final Item USB_DRIVE = Registry.register(Registries.ITEM, USB_DRIVE_KEY,
			new UsbDriveItem(new Item.Settings().registryKey(USB_DRIVE_KEY).maxCount(1)));

	public static final Item RADAR_MK1 = Registry.register(Registries.ITEM, RADAR_MK1_KEY,
			new BlockItem(ModBlocks.RADAR_MK1, new Item.Settings()
					.registryKey(RADAR_MK1_KEY)
					.useBlockPrefixedTranslationKey()));

	public static final Item ARMORED_BLOCK_MK1 = Registry.register(Registries.ITEM, ARMORED_BLOCK_MK1_KEY,
			new BlockItem(ModBlocks.ARMORED_BLOCK_MK1, new Item.Settings()
					.registryKey(ARMORED_BLOCK_MK1_KEY)
					.useBlockPrefixedTranslationKey()));
	public static final Item ARMORED_BLOCK_MK2 = Registry.register(Registries.ITEM, ARMORED_BLOCK_MK2_KEY,
			new BlockItem(ModBlocks.ARMORED_BLOCK_MK2, new Item.Settings()
					.registryKey(ARMORED_BLOCK_MK2_KEY)
					.useBlockPrefixedTranslationKey()));
	public static final Item ARMORED_BLOCK_MK3 = Registry.register(Registries.ITEM, ARMORED_BLOCK_MK3_KEY,
			new BlockItem(ModBlocks.ARMORED_BLOCK_MK3, new Item.Settings()
					.registryKey(ARMORED_BLOCK_MK3_KEY)
					.useBlockPrefixedTranslationKey()));

	public static final Item ARMORED_DOOR_MK1 = Registry.register(Registries.ITEM, ARMORED_DOOR_MK1_KEY,
			new TallBlockItem(ModBlocks.ARMORED_DOOR_MK1, new Item.Settings()
					.registryKey(ARMORED_DOOR_MK1_KEY)
					.useBlockPrefixedTranslationKey()));
	public static final Item ARMORED_DOOR_MK2 = Registry.register(Registries.ITEM, ARMORED_DOOR_MK2_KEY,
			new TallBlockItem(ModBlocks.ARMORED_DOOR_MK2, new Item.Settings()
					.registryKey(ARMORED_DOOR_MK2_KEY)
					.useBlockPrefixedTranslationKey()));
	public static final Item ARMORED_DOOR_MK3 = Registry.register(Registries.ITEM, ARMORED_DOOR_MK3_KEY,
			new TallBlockItem(ModBlocks.ARMORED_DOOR_MK3, new Item.Settings()
					.registryKey(ARMORED_DOOR_MK3_KEY)
					.useBlockPrefixedTranslationKey()));

	public static final Item ARMOR_TOOL = Registry.register(Registries.ITEM, ARMOR_TOOL_KEY,
			new ArmorToolItem(new Item.Settings().registryKey(ARMOR_TOOL_KEY).maxCount(1)));

	public static final Item SAM_SITE = Registry.register(Registries.ITEM, SAM_SITE_KEY,
			new BlockItem(ModBlocks.SAM_SITE, new Item.Settings()
					.registryKey(SAM_SITE_KEY)
					.useBlockPrefixedTranslationKey()));

	public static final Item CIWS = Registry.register(Registries.ITEM, CIWS_KEY,
			new BlockItem(ModBlocks.CIWS, new Item.Settings()
					.registryKey(CIWS_KEY)
					.useBlockPrefixedTranslationKey()));

	/** Ammo for {@code SamSiteBlockEntity} - one consumed per interceptor launched. */
	public static final Item SAM_AMMO = Registry.register(Registries.ITEM, SAM_AMMO_KEY,
			new Item(new Item.Settings().registryKey(SAM_AMMO_KEY).maxCount(16)));

	/** Ammo for {@code CiwsBlockEntity} - one consumed per burst fired. */
	public static final Item CIWS_AMMO = Registry.register(Registries.ITEM, CIWS_AMMO_KEY,
			new Item(new Item.Settings().registryKey(CIWS_AMMO_KEY).maxCount(64)));

	public static final Item WIRE = Registry.register(Registries.ITEM, WIRE_KEY,
			new BlockItem(ModBlocks.WIRE, new Item.Settings()
					.registryKey(WIRE_KEY)
					.useBlockPrefixedTranslationKey()));

	private ModItems() {
	}

	public static void register() {
		ItemGroupEvents.modifyEntriesEvent(ModItemGroups.MAIN_KEY).register(entries -> {
			entries.add(ICBM_MISSILE);
			entries.add(MISSILE_LAUNCHER);
			entries.add(USB_DRIVE);
			entries.add(RADAR_MK1);
			entries.add(ARMORED_BLOCK_MK1);
			entries.add(ARMORED_BLOCK_MK2);
			entries.add(ARMORED_BLOCK_MK3);
			entries.add(ARMORED_DOOR_MK1);
			entries.add(ARMORED_DOOR_MK2);
			entries.add(ARMORED_DOOR_MK3);
			entries.add(ARMOR_TOOL);
			entries.add(SAM_SITE);
			entries.add(CIWS);
			entries.add(SAM_AMMO);
			entries.add(CIWS_AMMO);
			entries.add(WIRE);
		});
	}
}
