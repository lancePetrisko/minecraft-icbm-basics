package com.example.icbmbasics.screen;

import com.example.icbmbasics.network.LauncherScreenData;
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

public class MissileLauncherScreenHandler extends ScreenHandler {
	public static final int MISSILE_SLOT_X = 152;
	public static final int MISSILE_SLOT_Y = 30;

	private final Inventory inventory;
	private final BlockPos launcherPos;
	private final int initialTargetX;
	private final int initialTargetY;
	private final int initialTargetZ;
	private final boolean hasInitialTarget;

	/** Client-side constructor, called by the ExtendedScreenHandlerType. */
	public MissileLauncherScreenHandler(int syncId, PlayerInventory playerInventory, LauncherScreenData data) {
		this(syncId, playerInventory, new SimpleInventory(1), data.pos(),
				data.targetX(), data.targetY(), data.targetZ(), data.hasTarget());
	}

	/** Server-side constructor, called by the block entity. */
	public MissileLauncherScreenHandler(int syncId, PlayerInventory playerInventory, Inventory inventory, BlockPos pos) {
		this(syncId, playerInventory, inventory, pos, 0, 0, 0, false);
	}

	private MissileLauncherScreenHandler(int syncId, PlayerInventory playerInventory, Inventory inventory,
			BlockPos pos, int targetX, int targetY, int targetZ, boolean hasTarget) {
		super(ModScreenHandlers.MISSILE_LAUNCHER, syncId);

		this.inventory = inventory;
		this.launcherPos = pos;
		this.initialTargetX = targetX;
		this.initialTargetY = targetY;
		this.initialTargetZ = targetZ;
		this.hasInitialTarget = hasTarget;

		// The single missile ammo slot.
		this.addSlot(new Slot(inventory, 0, MISSILE_SLOT_X, MISSILE_SLOT_Y) {
			@Override
			public boolean canInsert(ItemStack stack) {
				return stack.isOf(ModItems.ICBM_MISSILE);
			}
		});

		// Player inventory + hotbar.
		this.addPlayerSlots(playerInventory, 8, 84);
	}

	public BlockPos getLauncherPos() {
		return this.launcherPos;
	}

	public int getInitialTargetX() {
		return this.initialTargetX;
	}

	public int getInitialTargetY() {
		return this.initialTargetY;
	}

	public int getInitialTargetZ() {
		return this.initialTargetZ;
	}

	public boolean hasInitialTarget() {
		return this.hasInitialTarget;
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

			if (slotIndex == 0) {
				// Missile slot -> player inventory.
				if (!this.insertItem(stack, 1, this.slots.size(), true)) {
					return ItemStack.EMPTY;
				}
			} else {
				// Player inventory -> missile slot.
				if (!this.insertItem(stack, 0, 1, false)) {
					return ItemStack.EMPTY;
				}
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
