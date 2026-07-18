package com.example.icbmbasics.client.screen;

import com.example.icbmbasics.network.SetTargetPayload;
import com.example.icbmbasics.screen.MissileLauncherScreenHandler;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.text.Text;

import net.minecraft.client.input.KeyInput;

/**
 * Launcher GUI: three coordinate fields, a missile ammo slot, and a
 * "Confirm Target" button. The background is drawn with plain fills, so no
 * GUI texture is required.
 */
public class MissileLauncherScreen extends HandledScreen<MissileLauncherScreenHandler> {
	private static final int PANEL_COLOR = 0xFFC6C6C6;
	private static final int PANEL_BORDER_DARK = 0xFF555555;
	private static final int PANEL_BORDER_LIGHT = 0xFFFFFFFF;
	private static final int SLOT_COLOR = 0xFF8B8B8B;
	private static final int LABEL_COLOR = 0xFF404040;

	private TextFieldWidget xField;
	private TextFieldWidget yField;
	private TextFieldWidget zField;
	private Text statusText = Text.empty();

	public MissileLauncherScreen(MissileLauncherScreenHandler handler, PlayerInventory inventory, Text title) {
		super(handler, inventory, title);
		this.backgroundWidth = 176;
		this.backgroundHeight = 166;
	}

	@Override
	protected void init() {
		super.init();

		int left = this.x;
		int top = this.y;

		this.xField = createCoordField(left + 10, top + 24, "X");
		this.yField = createCoordField(left + 52, top + 24, "Y");
		this.zField = createCoordField(left + 94, top + 24, "Z");

		if (this.handler.hasInitialTarget()) {
			this.xField.setText(Integer.toString(this.handler.getInitialTargetX()));
			this.yField.setText(Integer.toString(this.handler.getInitialTargetY()));
			this.zField.setText(Integer.toString(this.handler.getInitialTargetZ()));
			this.statusText = Text.translatable("gui.icbmbasics.target_locked");
		}

		this.addDrawableChild(this.xField);
		this.addDrawableChild(this.yField);
		this.addDrawableChild(this.zField);

		this.addDrawableChild(ButtonWidget.builder(
						Text.translatable("gui.icbmbasics.confirm_target"),
						button -> this.confirmTarget())
				.dimensions(left + 10, top + 48, 120, 20)
				.build());
	}

	private TextFieldWidget createCoordField(int fieldX, int fieldY, String placeholder) {
		TextFieldWidget field = new TextFieldWidget(this.textRenderer, fieldX, fieldY, 38, 18,
				Text.literal(placeholder));
		field.setMaxLength(9);
		field.setPlaceholder(Text.literal(placeholder));
		// Only allow (optionally negative) integers.
		field.setTextPredicate(text -> text.matches("-?\\d*"));
		return field;
	}

	private void confirmTarget() {
		Integer tx = parse(this.xField.getText());
		Integer ty = parse(this.yField.getText());
		Integer tz = parse(this.zField.getText());

		if (tx == null || ty == null || tz == null) {
			this.statusText = Text.translatable("gui.icbmbasics.invalid_coords");
			return;
		}

		ClientPlayNetworking.send(new SetTargetPayload(this.handler.getLauncherPos(), tx, ty, tz));
		this.statusText = Text.translatable("gui.icbmbasics.target_locked");
	}

	private static Integer parse(String text) {
		try {
			return Integer.parseInt(text.trim());
		} catch (NumberFormatException e) {
			return null;
		}
	}

	@Override
	protected void drawBackground(DrawContext context, float delta, int mouseX, int mouseY) {
		int left = this.x;
		int top = this.y;
		int right = left + this.backgroundWidth;
		int bottom = top + this.backgroundHeight;

		// Simple beveled panel instead of a texture.
		context.fill(left - 1, top - 1, right + 1, bottom + 1, PANEL_BORDER_DARK);
		context.fill(left, top, right, bottom, PANEL_COLOR);
		context.fill(left, top, right, top + 1, PANEL_BORDER_LIGHT);
		context.fill(left, top, left + 1, bottom, PANEL_BORDER_LIGHT);

		// Missile slot backdrop (slot itself is drawn by the screen handler).
		int slotX = left + MissileLauncherScreenHandler.MISSILE_SLOT_X - 1;
		int slotY = top + MissileLauncherScreenHandler.MISSILE_SLOT_Y - 1;
		context.fill(slotX, slotY, slotX + 18, slotY + 18, SLOT_COLOR);

		// Player inventory backdrop rows.
		for (int row = 0; row < 3; row++) {
			for (int col = 0; col < 9; col++) {
				int sx = left + 7 + col * 18;
				int sy = top + 83 + row * 18;
				context.fill(sx, sy, sx + 18, sy + 18, SLOT_COLOR);
			}
		}
		for (int col = 0; col < 9; col++) {
			int sx = left + 7 + col * 18;
			int sy = top + 141;
			context.fill(sx, sy, sx + 18, sy + 18, SLOT_COLOR);
		}
	}

	@Override
	protected void drawForeground(DrawContext context, int mouseX, int mouseY) {
		// Custom layout, so we draw our own labels instead of the defaults.
		context.drawText(this.textRenderer, this.title, this.titleX, this.titleY, LABEL_COLOR, false);
		context.drawText(this.textRenderer,
				Text.translatable("gui.icbmbasics.ammo"), 138, 19, LABEL_COLOR, false);
		context.drawText(this.textRenderer, this.statusText, 10, 72, LABEL_COLOR, false);
	}

	@Override
	public void render(DrawContext context, int mouseX, int mouseY, float delta) {
		super.render(context, mouseX, mouseY, delta);
		this.drawMouseoverTooltip(context, mouseX, mouseY);
	}

	@Override
	public boolean keyPressed(KeyInput input) {
		if (input.isEscape()) {
			return super.keyPressed(input);
		}
		// While a coordinate field is focused, let it consume keys so typing
		// the inventory key ("E") doesn't close the screen.
		for (TextFieldWidget field : new TextFieldWidget[]{this.xField, this.yField, this.zField}) {
			if (field != null && field.isFocused()) {
				field.keyPressed(input);
				return true;
			}
		}
		return super.keyPressed(input);
	}
}
