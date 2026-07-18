package com.example.icbmbasics.registry;

import com.example.icbmbasics.ICBMBasics;

import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroups;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;

public final class ModItems {
	public static final RegistryKey<Item> ICBM_MISSILE_KEY =
			RegistryKey.of(RegistryKeys.ITEM, ICBMBasics.id("icbm_missile"));
	public static final RegistryKey<Item> MISSILE_LAUNCHER_KEY =
			RegistryKey.of(RegistryKeys.ITEM, ICBMBasics.id("missile_launcher"));

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

	private ModItems() {
	}

	public static void register() {
		ItemGroupEvents.modifyEntriesEvent(ItemGroups.COMBAT).register(entries -> {
			entries.add(ICBM_MISSILE);
			entries.add(MISSILE_LAUNCHER);
		});
	}
}
