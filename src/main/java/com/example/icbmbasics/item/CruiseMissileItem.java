package com.example.icbmbasics.item;

import java.util.function.Consumer;

import net.minecraft.component.type.TooltipDisplayComponent;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

/**
 * Ammo for the missile launcher, same as {@code ICBM_MISSILE}, just flagged
 * as a cruise missile on the spawned {@code MissileEntity} (see
 * {@code MissileLauncherBlockEntity#tryLaunch}) - flies terrain-hugging and
 * can juke a homing SAM shot once, with a smaller effective radar detection
 * radius to go with it. Only difference from a plain {@link Item} is this
 * tooltip line advertising that.
 */
public class CruiseMissileItem extends Item {
	public CruiseMissileItem(Settings settings) {
		super(settings);
	}

	@Override
	public void appendTooltip(ItemStack stack, TooltipContext context, TooltipDisplayComponent displayComponent,
			Consumer<Text> textConsumer, TooltipType type) {
		super.appendTooltip(stack, context, displayComponent, textConsumer, type);
		textConsumer.accept(Text.translatable("item.icbmbasics.cruise_missile.tooltip").formatted(Formatting.GRAY));
	}
}
