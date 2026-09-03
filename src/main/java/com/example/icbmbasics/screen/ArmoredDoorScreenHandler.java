package com.example.icbmbasics.screen;

import com.example.icbmbasics.block.entity.ArmoredDoorBlockEntity;
import com.example.icbmbasics.network.ArmoredDoorScreenData;
import com.example.icbmbasics.registry.ModScreenHandlers;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.util.math.BlockPos;

/**
 * Backs the armored door's keypad GUI. No slots - purely a code-entry
 * front end. The client only ever sees {@code codeSet}/{@code isOwner}/
 * {@code isAuthorized} (which mode to show, and whether to offer the reset
 * button); the actual code and remembered-player list live server-side on
 * {@link ArmoredDoorBlockEntity} and are validated there via
 * {@code SubmitDoorCodePayload}/{@code ResetDoorCodePayload}.
 */
public class ArmoredDoorScreenHandler extends ScreenHandler {
	private final BlockPos doorPos;
	private final boolean codeSet;
	private final boolean owner;
	private final boolean authorized;

	/** Client-side constructor, called by the ExtendedScreenHandlerType. */
	public ArmoredDoorScreenHandler(int syncId, PlayerInventory playerInventory, ArmoredDoorScreenData data) {
		this(syncId, data.pos(), data.codeSet(), data.isOwner(), data.isAuthorized());
	}

	/** Server-side constructor, called by the block entity. */
	public ArmoredDoorScreenHandler(int syncId, ArmoredDoorBlockEntity door, BlockPos pos, PlayerEntity player) {
		this(syncId, pos, door.isCodeSet(), door.isOwner(player.getUuid()), door.isAuthorized(player.getUuid()));
	}

	private ArmoredDoorScreenHandler(int syncId, BlockPos pos, boolean codeSet, boolean owner, boolean authorized) {
		super(ModScreenHandlers.ARMORED_DOOR, syncId);
		this.doorPos = pos;
		this.codeSet = codeSet;
		this.owner = owner;
		this.authorized = authorized;
	}

	public BlockPos getDoorPos() {
		return this.doorPos;
	}

	public boolean isCodeSet() {
		return this.codeSet;
	}

	/** Whether the viewing player is this door's owner (set the current code) - the only one who can reset it. */
	public boolean isOwner() {
		return this.owner;
	}

	/** Whether the viewing player already proved the code and could have opened the door directly (they sneak-clicked to get here instead). */
	public boolean isAuthorized() {
		return this.authorized;
	}

	@Override
	public boolean canUse(PlayerEntity player) {
		return player.getBlockPos().isWithinDistance(this.doorPos, 8.0);
	}

	@Override
	public ItemStack quickMove(PlayerEntity player, int slotIndex) {
		return ItemStack.EMPTY;
	}
}
