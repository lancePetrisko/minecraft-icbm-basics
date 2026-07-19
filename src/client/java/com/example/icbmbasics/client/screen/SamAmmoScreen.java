package com.example.icbmbasics.client.screen;

import com.example.icbmbasics.registry.ModItems;
import com.example.icbmbasics.screen.SamAmmoScreenHandler;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.text.Text;

/**
 * Just the SAM site's ammo slot plus a plainly-drawn "Ammo: X / Y" readout,
 * so the count is visible at a glance and not just implied by the item icon.
 */
public class SamAmmoScreen extends HandledScreen<SamAmmoScreenHandler> {
	private static final int PANEL_COLOR = 0xFFC6C6C6;
	private static final int PANEL_BORDER_DARK = 0xFF555555;
	private static final int PANEL_BORDER_LIGHT = 0xFFFFFFFF;
	private static final int SLOT_COLOR = 0xFF8B8B8B;
	private static final int LABEL_COLOR = 0xFF404040;

	private static final int COUNT_LABEL_Y = 6;
	private static final int PLAYER_INV_Y = SamAmmoScreenHandler.PLAYER_INV_Y;

	public SamAmmoScreen(SamAmmoScreenHandler handler, PlayerInventory inventory, Text title) {
		super(handler, inventory, title);
		this.backgroundWidth = 176;
		this.backgroundHeight = PLAYER_INV_Y + 58 + 18 + 7;
	}

	@Override
	protected void drawBackground(DrawContext context, float delta, int mouseX, int mouseY) {
		int left = this.x;
		int top = this.y;
		int right = left + this.backgroundWidth;
		int bottom = top + this.backgroundHeight;

		context.fill(left - 1, top - 1, right + 1, bottom + 1, PANEL_BORDER_DARK);
		context.fill(left, top, right, bottom, PANEL_COLOR);
		context.fill(left, top, right, top + 1, PANEL_BORDER_LIGHT);
		context.fill(left, top, left + 1, bottom, PANEL_BORDER_LIGHT);

		int slotX = left + SamAmmoScreenHandler.AMMO_SLOT_X - 1;
		int slotY = top + SamAmmoScreenHandler.AMMO_SLOT_Y - 1;
		context.fill(slotX, slotY, slotX + 18, slotY + 18, SLOT_COLOR);

		for (int row = 0; row < 3; row++) {
			for (int col = 0; col < 9; col++) {
				int sx = left + 7 + col * 18;
				int sy = top + PLAYER_INV_Y + row * 18;
				context.fill(sx, sy, sx + 18, sy + 18, SLOT_COLOR);
			}
		}
		for (int col = 0; col < 9; col++) {
			int sx = left + 7 + col * 18;
			int sy = top + PLAYER_INV_Y + 58;
			context.fill(sx, sy, sx + 18, sy + 18, SLOT_COLOR);
		}
	}

	@Override
	protected void drawForeground(DrawContext context, int mouseX, int mouseY) {
		context.drawText(this.textRenderer, this.title, this.titleX, this.titleY, LABEL_COLOR, false);

		int count = this.handler.getSlot(0).getStack().getCount();
		context.drawText(this.textRenderer,
				Text.translatable("gui.icbmbasics.ammo_count", count, ModItems.SAM_AMMO.getMaxCount()),
				this.backgroundWidth - 80, COUNT_LABEL_Y, LABEL_COLOR, false);
	}

	@Override
	public void render(DrawContext context, int mouseX, int mouseY, float delta) {
		super.render(context, mouseX, mouseY, delta);
		this.drawMouseoverTooltip(context, mouseX, mouseY);
	}
}
