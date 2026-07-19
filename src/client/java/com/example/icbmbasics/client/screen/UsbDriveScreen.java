package com.example.icbmbasics.client.screen;

import java.util.List;

import com.example.icbmbasics.network.DeleteDriveWaypointPayload;
import com.example.icbmbasics.network.SaveDriveWaypointPayload;
import com.example.icbmbasics.network.Waypoint;
import com.example.icbmbasics.screen.UsbDriveScreenHandler;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;

import net.minecraft.client.input.KeyInput;

/**
 * The USB drive's own GUI: X/Y/Z + name fields, a "Use my current location"
 * button, a Save button, and a scrollable named-waypoint list (click name to
 * load into the fields, click "x" to delete). No item slots - the drive
 * edits its own held-stack data component. Plain-fill background, matching
 * {@code MissileLauncherScreen}'s custom-drawn style.
 */
public class UsbDriveScreen extends HandledScreen<UsbDriveScreenHandler> {
	private static final int PANEL_COLOR = 0xFFC6C6C6;
	private static final int PANEL_BORDER_DARK = 0xFF555555;
	private static final int PANEL_BORDER_LIGHT = 0xFFFFFFFF;
	private static final int LABEL_COLOR = 0xFF404040;
	private static final int DELETE_COLOR = 0xFFAA0000;

	private static final int COORD_ROW_Y = 20;
	private static final int NAME_ROW_Y = 42;
	private static final int USE_LOC_ROW_Y = 62;
	private static final int STATUS_TEXT_Y = 82;
	private static final int HEADER_Y = 96;
	private static final int LIST_X = 10;
	private static final int LIST_Y = 108;
	private static final int LIST_ROW_HEIGHT = 10;
	private static final int VISIBLE_ROWS = 6;
	private static final int DELETE_X = 150;

	private TextFieldWidget xField;
	private TextFieldWidget yField;
	private TextFieldWidget zField;
	private TextFieldWidget nameField;
	private Text statusText = Text.empty();
	private int scrollOffset;

	public UsbDriveScreen(UsbDriveScreenHandler handler, PlayerInventory inventory, Text title) {
		super(handler, inventory, title);
		this.backgroundWidth = 180;
		this.backgroundHeight = LIST_Y + VISIBLE_ROWS * LIST_ROW_HEIGHT + 10;
	}

	@Override
	protected void init() {
		super.init();

		int left = this.x;
		int top = this.y;

		this.xField = createCoordField(left + 10, top + COORD_ROW_Y, "X");
		this.yField = createCoordField(left + 52, top + COORD_ROW_Y, "Y");
		this.zField = createCoordField(left + 94, top + COORD_ROW_Y, "Z");
		this.addDrawableChild(this.xField);
		this.addDrawableChild(this.yField);
		this.addDrawableChild(this.zField);

		this.nameField = new TextFieldWidget(this.textRenderer, left + 10, top + NAME_ROW_Y, 100, 16,
				Text.translatable("gui.icbmbasics.name_placeholder"));
		this.nameField.setMaxLength(32);
		this.nameField.setPlaceholder(Text.translatable("gui.icbmbasics.name_placeholder"));
		this.addDrawableChild(this.nameField);

		this.addDrawableChild(ButtonWidget.builder(
						Text.translatable("gui.icbmbasics.save_waypoint"),
						button -> this.saveWaypoint())
				.dimensions(left + 114, top + NAME_ROW_Y, 56, 16)
				.build());

		this.addDrawableChild(ButtonWidget.builder(
						Text.translatable("gui.icbmbasics.use_current_location"),
						button -> this.useCurrentLocation())
				.dimensions(left + 10, top + USE_LOC_ROW_Y, 160, 16)
				.build());
	}

	private TextFieldWidget createCoordField(int fieldX, int fieldY, String placeholder) {
		TextFieldWidget field = new TextFieldWidget(this.textRenderer, fieldX, fieldY, 38, 18,
				Text.literal(placeholder));
		field.setMaxLength(9);
		field.setPlaceholder(Text.literal(placeholder));
		field.setTextPredicate(text -> text.matches("-?\\d*"));
		return field;
	}

	private void useCurrentLocation() {
		BlockPos pos = this.client.player.getBlockPos();
		this.xField.setText(Integer.toString(pos.getX()));
		this.yField.setText(Integer.toString(pos.getY()));
		this.zField.setText(Integer.toString(pos.getZ()));
	}

	private void saveWaypoint() {
		String name = this.nameField.getText().trim();
		Integer tx = parse(this.xField.getText());
		Integer ty = parse(this.yField.getText());
		Integer tz = parse(this.zField.getText());

		if (name.isEmpty()) {
			this.statusText = Text.translatable("gui.icbmbasics.invalid_name");
			return;
		}
		if (tx == null || ty == null || tz == null) {
			this.statusText = Text.translatable("gui.icbmbasics.invalid_coords");
			return;
		}

		ClientPlayNetworking.send(new SaveDriveWaypointPayload(this.handler.getHand(), name, tx, ty, tz));
		this.statusText = Text.translatable("gui.icbmbasics.waypoint_saved");
	}

	private void loadWaypoint(Waypoint waypoint) {
		this.xField.setText(Integer.toString(waypoint.x()));
		this.yField.setText(Integer.toString(waypoint.y()));
		this.zField.setText(Integer.toString(waypoint.z()));
		this.nameField.setText(waypoint.name());
	}

	private void deleteWaypoint(Waypoint waypoint) {
		ClientPlayNetworking.send(new DeleteDriveWaypointPayload(this.handler.getHand(), waypoint.name()));
	}

	private static Integer parse(String text) {
		try {
			return Integer.parseInt(text.trim());
		} catch (NumberFormatException e) {
			return null;
		}
	}

	private boolean handleWaypointClick(double mouseX, double mouseY) {
		List<Waypoint> waypoints = this.handler.getWaypoints();
		int left = this.x;
		int top = this.y;

		for (int i = 0; i < VISIBLE_ROWS; i++) {
			int index = this.scrollOffset + i;
			if (index >= waypoints.size()) {
				break;
			}
			int rowY = top + LIST_Y + i * LIST_ROW_HEIGHT;
			if (mouseY >= rowY && mouseY < rowY + LIST_ROW_HEIGHT) {
				Waypoint waypoint = waypoints.get(index);
				if (mouseX >= left + DELETE_X && mouseX < left + DELETE_X + 10) {
					this.deleteWaypoint(waypoint);
				} else if (mouseX >= left + LIST_X && mouseX < left + DELETE_X) {
					this.loadWaypoint(waypoint);
				}
				return true;
			}
		}
		return false;
	}

	@Override
	public boolean mouseClicked(Click click, boolean doubled) {
		if (this.handleWaypointClick(click.x(), click.y())) {
			return true;
		}
		return super.mouseClicked(click, doubled);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
		int maxScroll = Math.max(0, this.handler.getWaypoints().size() - VISIBLE_ROWS);
		if (maxScroll > 0) {
			this.scrollOffset = MathHelper.clamp(
					this.scrollOffset - (int) Math.signum(verticalAmount), 0, maxScroll);
			return true;
		}
		return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
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
		context.drawText(this.textRenderer, this.title, this.titleX, this.titleY, LABEL_COLOR, false);
		context.drawText(this.textRenderer, this.statusText, 10, STATUS_TEXT_Y, LABEL_COLOR, false);

		context.drawText(this.textRenderer,
				Text.translatable("gui.icbmbasics.saved_targets"), LIST_X, HEADER_Y, LABEL_COLOR, false);

		List<Waypoint> waypoints = this.handler.getWaypoints();
		if (waypoints.isEmpty()) {
			context.drawText(this.textRenderer,
					Text.translatable("gui.icbmbasics.no_waypoints"), LIST_X, LIST_Y, LABEL_COLOR, false);
		} else {
			for (int i = 0; i < VISIBLE_ROWS; i++) {
				int index = this.scrollOffset + i;
				if (index >= waypoints.size()) {
					break;
				}
				Waypoint waypoint = waypoints.get(index);
				int rowY = LIST_Y + i * LIST_ROW_HEIGHT;
				String label = this.textRenderer.trimToWidth(waypoint.name(), DELETE_X - LIST_X - 4);
				context.drawText(this.textRenderer, Text.literal(label), LIST_X, rowY, LABEL_COLOR, false);
				context.drawText(this.textRenderer, Text.literal("x"), DELETE_X, rowY, DELETE_COLOR, false);
			}
		}
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
		for (TextFieldWidget field : new TextFieldWidget[]{this.xField, this.yField, this.zField, this.nameField}) {
			if (field != null && field.isFocused()) {
				field.keyPressed(input);
				return true;
			}
		}
		return super.keyPressed(input);
	}
}
