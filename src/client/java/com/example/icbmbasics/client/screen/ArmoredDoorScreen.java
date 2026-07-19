package com.example.icbmbasics.client.screen;

import com.example.icbmbasics.network.SubmitDoorCodePayload;
import com.example.icbmbasics.screen.ArmoredDoorScreenHandler;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.text.Text;

/**
 * A numeric keypad: 0-9, Clear, Submit, and a masked display of what's been
 * typed so far. Shows "set a code" or "enter code" copy depending on whether
 * the door already has one. Purely a front end - submitting always closes
 * the screen and lets the server validate/react.
 */
public class ArmoredDoorScreen extends HandledScreen<ArmoredDoorScreenHandler> {
	private static final int PANEL_COLOR = 0xFFC6C6C6;
	private static final int PANEL_BORDER_DARK = 0xFF555555;
	private static final int PANEL_BORDER_LIGHT = 0xFFFFFFFF;
	private static final int LABEL_COLOR = 0xFF404040;

	private static final int MAX_DIGITS = 6;
	private static final int DISPLAY_Y = 22;
	private static final int PAD_TOP = 36;
	private static final int BUTTON_SIZE = 20;
	private static final int BUTTON_GAP = 4;
	private static final int PAD_COLS = 3;

	private StringBuilder entered = new StringBuilder();
	private Text status = Text.empty();

	public ArmoredDoorScreen(ArmoredDoorScreenHandler handler, PlayerInventory inventory, Text title) {
		super(handler, inventory, title);
		this.backgroundWidth = PAD_COLS * (BUTTON_SIZE + BUTTON_GAP) + BUTTON_GAP;
		this.backgroundHeight = PAD_TOP + 4 * (BUTTON_SIZE + BUTTON_GAP) + 10;
	}

	@Override
	protected void init() {
		super.init();
		int left = this.x;
		int top = this.y + PAD_TOP;

		// 1-9 in a 3x3 grid, then Clear / 0 / Submit on the last row.
		for (int digit = 1; digit <= 9; digit++) {
			int col = (digit - 1) % PAD_COLS;
			int row = (digit - 1) / PAD_COLS;
			this.addDigitButton(left, top, col, row, digit);
		}
		this.addDrawableChild(ButtonWidget.builder(Text.literal("C"), button -> this.clear())
				.dimensions(left + BUTTON_GAP, top + 3 * (BUTTON_SIZE + BUTTON_GAP), BUTTON_SIZE, BUTTON_SIZE)
				.build());
		this.addDigitButton(left, top, 1, 3, 0);
		this.addDrawableChild(ButtonWidget.builder(Text.translatable("gui.icbmbasics.submit_code"), button -> this.submit())
				.dimensions(left + BUTTON_GAP + 2 * (BUTTON_SIZE + BUTTON_GAP), top + 3 * (BUTTON_SIZE + BUTTON_GAP),
						BUTTON_SIZE, BUTTON_SIZE)
				.build());
	}

	private void addDigitButton(int left, int top, int col, int row, int digit) {
		this.addDrawableChild(ButtonWidget.builder(Text.literal(Integer.toString(digit)),
						button -> this.appendDigit(digit))
				.dimensions(left + BUTTON_GAP + col * (BUTTON_SIZE + BUTTON_GAP), top + row * (BUTTON_SIZE + BUTTON_GAP),
						BUTTON_SIZE, BUTTON_SIZE)
				.build());
	}

	private void appendDigit(int digit) {
		if (this.entered.length() < MAX_DIGITS) {
			this.entered.append(digit);
		}
	}

	private void clear() {
		this.entered.setLength(0);
	}

	private void submit() {
		if (this.entered.isEmpty()) {
			this.status = Text.translatable("gui.icbmbasics.enter_code_first");
			return;
		}
		int code = Integer.parseInt(this.entered.toString());
		ClientPlayNetworking.send(new SubmitDoorCodePayload(this.handler.getDoorPos(), code));
		this.close();
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
	}

	@Override
	protected void drawForeground(DrawContext context, int mouseX, int mouseY) {
		Text mode = this.handler.isCodeSet()
				? Text.translatable("gui.icbmbasics.enter_code")
				: Text.translatable("gui.icbmbasics.set_code");
		context.drawText(this.textRenderer, mode, 6, 6, LABEL_COLOR, false);

		String masked = "*".repeat(this.entered.length());
		context.drawText(this.textRenderer, Text.literal(masked.isEmpty() ? "-" : masked), 6, DISPLAY_Y, LABEL_COLOR, false);
		context.drawText(this.textRenderer, this.status, 6, DISPLAY_Y + 10, LABEL_COLOR, false);
	}

	@Override
	public void render(DrawContext context, int mouseX, int mouseY, float delta) {
		super.render(context, mouseX, mouseY, delta);
		this.drawMouseoverTooltip(context, mouseX, mouseY);
	}
}
