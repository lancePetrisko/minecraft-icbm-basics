package com.example.icbmbasics.client.screen;

import com.example.icbmbasics.network.TriggerDetonatorPayload;
import com.example.icbmbasics.screen.DetonatorScreenHandler;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.text.Text;

/**
 * A red "DETONATE" button behind a striped launch-protection cover. The
 * cover has to be flipped up (one click) before the button underneath is
 * clickable at all - purely a client-side gate, since the server
 * independently re-validates the detonator's link every time it actually
 * receives {@code TriggerDetonatorPayload} regardless of what the GUI shows.
 */
public class DetonatorScreen extends HandledScreen<DetonatorScreenHandler> {
	private static final int PANEL_COLOR = 0xFFC6C6C6;
	private static final int PANEL_BORDER_DARK = 0xFF555555;
	private static final int PANEL_BORDER_LIGHT = 0xFFFFFFFF;
	private static final int LABEL_COLOR = 0xFF404040;
	private static final int COVER_COLOR = 0xFFAA8800;
	private static final int COVER_STRIPE_COLOR = 0xFF221800;
	private static final int BUTTON_AREA_COLOR = 0xFF1A1A1A;
	private static final int RESULT_COLOR = 0xFFCC0000;

	private static final int BUTTON_LEFT = 30;
	private static final int BUTTON_TOP = 24;
	private static final int BUTTON_SIZE = 80;
	private static final int STATUS_Y = BUTTON_TOP + BUTTON_SIZE + 12;

	private boolean coverOpen = false;
	private ButtonWidget coverButton;
	private ButtonWidget detonateButton;

	public DetonatorScreen(DetonatorScreenHandler handler, PlayerInventory inventory, Text title) {
		super(handler, inventory, title);
		this.backgroundWidth = BUTTON_LEFT * 2 + BUTTON_SIZE;
		this.backgroundHeight = STATUS_Y + 20;
	}

	@Override
	protected void init() {
		super.init();
		int left = this.x + BUTTON_LEFT;
		int top = this.y + BUTTON_TOP;

		this.coverButton = ButtonWidget.builder(
						Text.translatable("gui.icbmbasics.flip_cover"),
						button -> this.flipCover())
				.dimensions(left, top, BUTTON_SIZE, BUTTON_SIZE)
				.build();
		this.addDrawableChild(this.coverButton);

		this.detonateButton = ButtonWidget.builder(
						Text.translatable("gui.icbmbasics.detonate"),
						button -> this.detonate())
				.dimensions(left, top, BUTTON_SIZE, BUTTON_SIZE)
				.build();
		this.detonateButton.visible = false;
		this.detonateButton.active = false;
		this.addDrawableChild(this.detonateButton);
	}

	private void flipCover() {
		this.coverOpen = true;
		this.coverButton.visible = false;
		this.coverButton.active = false;
		this.detonateButton.visible = true;
		this.detonateButton.active = true;
	}

	private void detonate() {
		if (!this.coverOpen) {
			return;
		}
		ClientPlayNetworking.send(new TriggerDetonatorPayload(this.handler.getHand()));
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

		int buttonLeft = left + BUTTON_LEFT;
		int buttonTop = top + BUTTON_TOP;
		context.fill(buttonLeft, buttonTop, buttonLeft + BUTTON_SIZE, buttonTop + BUTTON_SIZE, BUTTON_AREA_COLOR);

		if (!this.coverOpen) {
			context.fill(buttonLeft, buttonTop, buttonLeft + BUTTON_SIZE, buttonTop + BUTTON_SIZE, COVER_COLOR);
			for (int i = 0; i < BUTTON_SIZE; i += 8) {
				context.fill(buttonLeft + i, buttonTop, buttonLeft + i + 4, buttonTop + BUTTON_SIZE, COVER_STRIPE_COLOR);
			}
		}
	}

	@Override
	protected void drawForeground(DrawContext context, int mouseX, int mouseY) {
		context.drawText(this.textRenderer, this.title, this.titleX, this.titleY, LABEL_COLOR, false);

		Text status = this.handler.isLinked()
				? Text.translatable("gui.icbmbasics.detonator_ready")
				: Text.translatable("gui.icbmbasics.detonator_unlinked");
		context.drawText(this.textRenderer, status, BUTTON_LEFT, STATUS_Y, LABEL_COLOR, false);

		String result = this.handler.getResultMessage();
		if (!result.isEmpty()) {
			context.drawText(this.textRenderer, Text.literal(result), BUTTON_LEFT, STATUS_Y + 10, RESULT_COLOR, false);
		}
	}

	@Override
	public void render(DrawContext context, int mouseX, int mouseY, float delta) {
		super.render(context, mouseX, mouseY, delta);
		this.drawMouseoverTooltip(context, mouseX, mouseY);
	}
}
