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
 * front end. The client only ever sees {@code codeSet} (which mode to show);
 * the actual code lives server-side on {@link ArmoredDoorBlockEntity} and is
 * validated there via {@code SubmitDoorCodePayload}.
 */
public class ArmoredDoorScreenHandler extends ScreenHandler {
	private final BlockPos doorPos;
	private final boolean codeSet;

	/** Client-side constructor, called by the ExtendedScreenHandlerType. */
	public ArmoredDoorScreenHandler(int syncId, PlayerInventory playerInventory, ArmoredDoorScreenData data) {
		this(syncId, data.pos(), data.codeSet());
	}

	/** Server-side constructor, called by the block entity. */
	public ArmoredDoorScreenHandler(int syncId, ArmoredDoorBlockEntity door, BlockPos pos) {
		this(syncId, pos, door.isCodeSet());
	}

	private ArmoredDoorScreenHandler(int syncId, BlockPos pos, boolean codeSet) {
		super(ModScreenHandlers.ARMORED_DOOR, syncId);
		this.doorPos = pos;
		this.codeSet = codeSet;
	}

	public BlockPos getDoorPos() {
		return this.doorPos;
	}

	public boolean isCodeSet() {
		return this.codeSet;
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
