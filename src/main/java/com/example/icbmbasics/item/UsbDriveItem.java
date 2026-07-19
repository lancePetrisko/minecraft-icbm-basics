package com.example.icbmbasics.item;

import java.util.List;

import com.example.icbmbasics.network.UsbDriveScreenData;
import com.example.icbmbasics.network.Waypoint;
import com.example.icbmbasics.registry.ModComponents;
import com.example.icbmbasics.screen.UsbDriveScreenHandler;

import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerFactory;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.world.World;

/**
 * A portable named-waypoint list. Right-click in hand opens a GUI for
 * adding/naming/deleting coordinates, stored directly on the item stack via
 * {@link ModComponents#WAYPOINTS}. Slotting one into a missile launcher lets
 * its list be read alongside the launcher's own.
 */
public class UsbDriveItem extends Item {
	public UsbDriveItem(Settings settings) {
		super(settings);
	}

	@Override
	public ActionResult use(World world, PlayerEntity player, Hand hand) {
		if (!world.isClient() && player instanceof ServerPlayerEntity serverPlayer) {
			serverPlayer.openHandledScreen(new ExtendedScreenHandlerFactory<UsbDriveScreenData>() {
				@Override
				public Text getDisplayName() {
					return Text.translatable("item.icbmbasics.usb_drive");
				}

				@Override
				public ScreenHandler createMenu(int syncId, PlayerInventory playerInventory, PlayerEntity menuPlayer) {
					return new UsbDriveScreenHandler(syncId, hand, menuPlayer);
				}

				@Override
				public UsbDriveScreenData getScreenOpeningData(ServerPlayerEntity opener) {
					ItemStack stack = opener.getStackInHand(hand);
					List<Waypoint> waypoints = stack.getOrDefault(ModComponents.WAYPOINTS, List.of());
					return new UsbDriveScreenData(hand, waypoints);
				}
			});
		}
		return ActionResult.SUCCESS;
	}
}
