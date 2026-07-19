package com.example.icbmbasics.screen;

import java.util.ArrayList;
import java.util.List;

import com.example.icbmbasics.network.UsbDriveScreenData;
import com.example.icbmbasics.network.Waypoint;
import com.example.icbmbasics.registry.ModItems;
import com.example.icbmbasics.registry.ModScreenHandlers;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.util.Hand;

/**
 * Backs the USB drive's own GUI. Has no slots: it edits the waypoint list
 * stored directly on whichever hand is holding the drive.
 */
public class UsbDriveScreenHandler extends ScreenHandler {
	private final Hand hand;
	private final PlayerEntity player;
	private List<Waypoint> waypoints;

	/** Client-side constructor, called by the ExtendedScreenHandlerType. */
	public UsbDriveScreenHandler(int syncId, PlayerInventory playerInventory, UsbDriveScreenData data) {
		this(syncId, data.hand(), playerInventory.player, data.waypoints());
	}

	/** Server-side constructor, called by the item's screen factory. */
	public UsbDriveScreenHandler(int syncId, Hand hand, PlayerEntity player) {
		this(syncId, hand, player, List.of());
	}

	private UsbDriveScreenHandler(int syncId, Hand hand, PlayerEntity player, List<Waypoint> waypoints) {
		super(ModScreenHandlers.USB_DRIVE, syncId);
		this.hand = hand;
		this.player = player;
		this.waypoints = new ArrayList<>(waypoints);
	}

	public Hand getHand() {
		return this.hand;
	}

	public List<Waypoint> getWaypoints() {
		return this.waypoints;
	}

	public void setWaypoints(List<Waypoint> waypoints) {
		this.waypoints = new ArrayList<>(waypoints);
	}

	@Override
	public boolean canUse(PlayerEntity player) {
		ItemStack held = player.getStackInHand(this.hand);
		return player == this.player && held.isOf(ModItems.USB_DRIVE);
	}

	@Override
	public ItemStack quickMove(PlayerEntity player, int slotIndex) {
		return ItemStack.EMPTY;
	}
}
