package com.example.icbmbasics.item;

import java.util.Optional;

import com.example.icbmbasics.ICBMBasics;
import com.example.icbmbasics.storage.ArmorZoneStorage;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/**
 * Right-click any block to move the nearest armor zone's anchor there,
 * without changing which blocks already count against it. Reports the
 * zone's current count so players can gauge remaining budget.
 */
public class ArmorToolItem extends Item {
	public ArmorToolItem(Settings settings) {
		super(settings);
	}

	@Override
	public ActionResult useOnBlock(ItemUsageContext context) {
		World world = context.getWorld();
		if (!(world instanceof ServerWorld serverWorld)) {
			return ActionResult.SUCCESS;
		}

		BlockPos pos = context.getBlockPos();
		Optional<Integer> count = ArmorZoneStorage.get(serverWorld).reanchor(pos);

		PlayerEntity player = context.getPlayer();
		if (player != null) {
			Text message = count.isPresent()
					? Text.translatable("message.icbmbasics.armor_zone_reanchored",
							count.get(), ICBMBasics.CONFIG.armorZoneMaxBlocks)
					: Text.translatable("message.icbmbasics.armor_zone_none");
			player.sendMessage(message, true);
		}

		return ActionResult.SUCCESS;
	}
}
