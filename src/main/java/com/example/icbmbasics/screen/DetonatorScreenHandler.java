package com.example.icbmbasics.screen;

import com.example.icbmbasics.network.DetonatorScreenData;
import com.example.icbmbasics.registry.ModItems;
import com.example.icbmbasics.registry.ModScreenHandlers;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.util.Hand;

/**
 * Backs the remote detonator's own GUI. Has no slots: it just fires
 * {@code TriggerDetonatorPayload} and displays whatever result comes back.
 */
public class DetonatorScreenHandler extends ScreenHandler {
	private final Hand hand;
	private final PlayerEntity player;
	private final boolean linked;
	private String resultMessage = "";

	/** Client-side constructor, called by the ExtendedScreenHandlerType. */
	public DetonatorScreenHandler(int syncId, PlayerInventory playerInventory, DetonatorScreenData data) {
		this(syncId, data.hand(), playerInventory.player, data.linked());
	}

	/** Server-side constructor, called by the item's screen factory. */
	public DetonatorScreenHandler(int syncId, Hand hand, PlayerEntity player, boolean linked) {
		super(ModScreenHandlers.DETONATOR, syncId);
		this.hand = hand;
		this.player = player;
		this.linked = linked;
	}

	public Hand getHand() {
		return this.hand;
	}

	public boolean isLinked() {
		return this.linked;
	}

	public String getResultMessage() {
		return this.resultMessage;
	}

	public void setResultMessage(String resultMessage) {
		this.resultMessage = resultMessage;
	}

	@Override
	public boolean canUse(PlayerEntity player) {
		ItemStack held = player.getStackInHand(this.hand);
		return player == this.player && held.isOf(ModItems.REMOTE_DETONATOR);
	}

	@Override
	public ItemStack quickMove(PlayerEntity player, int slotIndex) {
		return ItemStack.EMPTY;
	}
}
