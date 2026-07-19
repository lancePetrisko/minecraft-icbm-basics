package com.example.icbmbasics.registry;

import com.example.icbmbasics.ICBMBasics;

import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup;
import net.minecraft.item.ItemGroup;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.text.Text;

/**
 * The mod's own creative-inventory tab, so its items aren't scattered across
 * vanilla tabs (they used to live in {@code ItemGroups.COMBAT}). Entries are
 * added in {@code ModItems.register()}, same as before, just targeting this
 * group instead.
 */
public final class ModItemGroups {
	public static final RegistryKey<ItemGroup> MAIN_KEY =
			RegistryKey.of(RegistryKeys.ITEM_GROUP, ICBMBasics.id("main"));

	public static final ItemGroup MAIN = Registry.register(Registries.ITEM_GROUP, MAIN_KEY,
			FabricItemGroup.builder()
					.displayName(Text.translatable("itemGroup.icbmbasics.main"))
					.icon(() -> new ItemStack(ModItems.ICBM_MISSILE))
					.build());

	private ModItemGroups() {
	}

	public static void register() {
		// Static initializer above handles registration.
	}
}
