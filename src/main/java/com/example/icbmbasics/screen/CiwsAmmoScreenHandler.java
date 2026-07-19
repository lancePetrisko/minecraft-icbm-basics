package com.example.icbmbasics.screen;

import com.example.icbmbasics.network.AmmoScreenData;
import com.example.icbmbasics.registry.ModItems;
import com.example.icbmbasics.registry.ModScreenHandlers;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.Inventory;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.util.math.BlockPos;

/** A single {@code CIWS_AMMO} slot plus the player's own inventory - just enough to see/refill ammo. */
public class CiwsAmmoScreenHandler extends ScreenHandler {
	public static final int AMMO_SLOT_X = 79;
	public static final int AMMO_SLOT_Y = 24;
	public static final int PLAYER_INV_Y = 51;

	private static final int SLOT_COUNT = 1;

	private final Inventory inventory;
	private final BlockPos ciwsPos;

	/** Client-side constructor, called by the ExtendedScreenHandlerType. */
	public CiwsAmmoScreenHandler(int syncId, PlayerInventory playerInventory, AmmoScreenData data) {
		this(syncId, playerInventory, new SimpleInventory(SLOT_COUNT), data.pos());
	}

	/** Server-side constructor, called by the block entity. */
	public CiwsAmmoScreenHandler(int syncId, PlayerInventory playerInventory, Inventory inventory, BlockPos pos) {
		super(ModScreenHandlers.CIWS, syncId);
		this.inventory = inventory;
		this.ciwsPos = pos;

		this.addSlot(new Slot(inventory, 0, AMMO_SLOT_X, AMMO_SLOT_Y) {
			@Override
			public boolean canInsert(ItemStack stack) {
				return stack.isOf(ModItems.CIWS_AMMO);
			}
		});

		this.addPlayerSlots(playerInventory, 8, PLAYER_INV_Y);
	}

	public BlockPos getCiwsPos() {
		return this.ciwsPos;
	}

	@Override
	public boolean canUse(PlayerEntity player) {
		return this.inventory.canPlayerUse(player);
	}

	@Override
	public ItemStack quickMove(PlayerEntity player, int slotIndex) {
		ItemStack moved = ItemStack.EMPTY;
		Slot slot = this.slots.get(slotIndex);

		if (slot.hasStack()) {
			ItemStack stack = slot.getStack();
			moved = stack.copy();

			if (slotIndex < SLOT_COUNT) {
				if (!this.insertItem(stack, SLOT_COUNT, this.slots.size(), true)) {
					return ItemStack.EMPTY;
				}
			} else if (stack.isOf(ModItems.CIWS_AMMO)) {
				if (!this.insertItem(stack, 0, SLOT_COUNT, false)) {
					return ItemStack.EMPTY;
				}
			} else {
				return ItemStack.EMPTY;
			}

			if (stack.isEmpty()) {
				slot.setStack(ItemStack.EMPTY);
			} else {
				slot.markDirty();
			}
		}

		return moved;
	}
}
