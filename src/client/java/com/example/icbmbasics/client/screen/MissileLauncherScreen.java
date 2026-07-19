package com.example.icbmbasics.client.screen;

import java.util.List;

import com.example.icbmbasics.network.DeleteLauncherWaypointPayload;
import com.example.icbmbasics.network.SaveLauncherWaypointPayload;
import com.example.icbmbasics.network.SetTargetPayload;
import com.example.icbmbasics.network.Waypoint;
import com.example.icbmbasics.screen.MissileLauncherScreenHandler;

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
 * Launcher GUI: three coordinate fields, a missile ammo slot, a USB drive
 * slot, a "Confirm Target" button, a "Use my current location" button, a
 * name field + Save button for the launcher's own named-waypoint list, and
 * two scrollable waypoint sections - the launcher's own list (editable) and
 * the slotted USB drive's list (read-only here; edited via the drive's own
 * GUI). The background is drawn with plain fills, so no GUI texture is
 * required.
 *
 * <p>The top (custom-drawn) section sizes itself to fit both waypoint
 * sections; the item-slot grid below it keeps vanilla 18px slot spacing
 * since that can't be shrunk without breaking item-icon alignment.
 */
public class MissileLauncherScreen extends HandledScreen<MissileLauncherScreenHandler> {
	private static final int PANEL_COLOR = 0xFFC6C6C6;
	private static final int PANEL_BORDER_DARK = 0xFF555555;
	private static final int PANEL_BORDER_LIGHT = 0xFFFFFFFF;
	private static final int SLOT_COLOR = 0xFF8B8B8B;
	private static final int LABEL_COLOR = 0xFF404040;
	private static final int DELETE_COLOR = 0xFFAA0000;
	private static final int DRIVE_LABEL_COLOR = 0xFF1A5FA8;

	private static final int COORD_ROW_Y = 6;
	private static final int CONFIRM_ROW_Y = 26;
	private static final int USE_LOC_ROW_Y = 46;
	private static final int STATUS_TEXT_Y = 66;
	private static final int NAME_FIELD_Y = 78;
	private static final int LIST_X = 10;
	private static final int LAUNCHER_HEADER_Y = 98;
	private static final int LAUNCHER_LIST_Y = 108;
	private static final int LAUNCHER_VISIBLE_ROWS = 4;
	private static final int LIST_ROW_HEIGHT = 10;
	private static final int DRIVE_HEADER_Y = LAUNCHER_LIST_Y + LAUNCHER_VISIBLE_ROWS * LIST_ROW_HEIGHT + 2;
	private static final int DRIVE_LIST_Y = DRIVE_HEADER_Y + 10;
	private static final int DRIVE_VISIBLE_ROWS = 3;
	private static final int DELETE_X = 168;
	private static final int PLAYER_INV_Y = MissileLauncherScreenHandler.PLAYER_INV_Y;

	private TextFieldWidget xField;
	private TextFieldWidget yField;
	private TextFieldWidget zField;
	private TextFieldWidget nameField;
	private Text statusText = Text.empty();
	private int launcherScrollOffset;
	private int driveScrollOffset;

	public MissileLauncherScreen(MissileLauncherScreenHandler handler, PlayerInventory inventory, Text title) {
		super(handler, inventory, title);
		this.backgroundWidth = 200;
		this.backgroundHeight = PLAYER_INV_Y + 58 + 18 + 7;
	}

	@Override
	protected void init() {
		super.init();

		int left = this.x;
		int top = this.y;

		this.xField = createCoordField(left + 10, top + COORD_ROW_Y, "X");
		this.yField = createCoordField(left + 52, top + COORD_ROW_Y, "Y");
		this.zField = createCoordField(left + 94, top + COORD_ROW_Y, "Z");

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
				.dimensions(left + 10, top + CONFIRM_ROW_Y, 120, 18)
				.build());

		this.addDrawableChild(ButtonWidget.builder(
						Text.translatable("gui.icbmbasics.use_current_location"),
						button -> this.useCurrentLocation())
				.dimensions(left + 10, top + USE_LOC_ROW_Y, 120, 18)
				.build());

		this.nameField = new TextFieldWidget(this.textRenderer, left + 10, top + NAME_FIELD_Y, 100, 16,
				Text.translatable("gui.icbmbasics.name_placeholder"));
		this.nameField.setMaxLength(32);
		this.nameField.setPlaceholder(Text.translatable("gui.icbmbasics.name_placeholder"));
		this.addDrawableChild(this.nameField);

		this.addDrawableChild(ButtonWidget.builder(
						Text.translatable("gui.icbmbasics.save_waypoint"),
						button -> this.saveWaypoint())
				.dimensions(left + 114, top + NAME_FIELD_Y, 76, 16)
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

		ClientPlayNetworking.send(new SaveLauncherWaypointPayload(this.handler.getLauncherPos(), name, tx, ty, tz));
		this.statusText = Text.translatable("gui.icbmbasics.waypoint_saved");
	}

	private void loadWaypoint(Waypoint waypoint) {
		this.xField.setText(Integer.toString(waypoint.x()));
		this.yField.setText(Integer.toString(waypoint.y()));
		this.zField.setText(Integer.toString(waypoint.z()));
		this.nameField.setText(waypoint.name());
	}

	private void deleteWaypoint(Waypoint waypoint) {
		ClientPlayNetworking.send(new DeleteLauncherWaypointPayload(this.handler.getLauncherPos(), waypoint.name()));
	}

	private static Integer parse(String text) {
		try {
			return Integer.parseInt(text.trim());
		} catch (NumberFormatException e) {
			return null;
		}
	}

	/** Returns true and consumes the click if it landed on a launcher waypoint row. */
	private boolean handleLauncherListClick(double mouseX, double mouseY) {
		List<Waypoint> waypoints = this.handler.getLauncherWaypoints();
		int left = this.x;
		int top = this.y;

		for (int i = 0; i < LAUNCHER_VISIBLE_ROWS; i++) {
			int index = this.launcherScrollOffset + i;
			if (index >= waypoints.size()) {
				break;
			}
			int rowY = top + LAUNCHER_LIST_Y + i * LIST_ROW_HEIGHT;
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

	/** Returns true and consumes the click if it landed on a (read-only) drive waypoint row. */
	private boolean handleDriveListClick(double mouseX, double mouseY) {
		List<Waypoint> waypoints = this.handler.getDriveWaypoints();
		int left = this.x;
		int top = this.y;

		for (int i = 0; i < DRIVE_VISIBLE_ROWS; i++) {
			int index = this.driveScrollOffset + i;
			if (index >= waypoints.size()) {
				break;
			}
			int rowY = top + DRIVE_LIST_Y + i * LIST_ROW_HEIGHT;
			if (mouseY >= rowY && mouseY < rowY + LIST_ROW_HEIGHT
					&& mouseX >= left + LIST_X && mouseX < left + DELETE_X) {
				this.loadWaypoint(waypoints.get(index));
				return true;
			}
		}
		return false;
	}

	@Override
	public boolean mouseClicked(Click click, boolean doubled) {
		if (this.handleLauncherListClick(click.x(), click.y())) {
			return true;
		}
		if (this.handleDriveListClick(click.x(), click.y())) {
			return true;
		}
		return super.mouseClicked(click, doubled);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
		int top = this.y;
		if (mouseY >= top + DRIVE_HEADER_Y) {
			int maxScroll = Math.max(0, this.handler.getDriveWaypoints().size() - DRIVE_VISIBLE_ROWS);
			if (maxScroll > 0) {
				this.driveScrollOffset = MathHelper.clamp(
						this.driveScrollOffset - (int) Math.signum(verticalAmount), 0, maxScroll);
				return true;
			}
		} else if (mouseY >= top + LAUNCHER_HEADER_Y) {
			int maxScroll = Math.max(0, this.handler.getLauncherWaypoints().size() - LAUNCHER_VISIBLE_ROWS);
			if (maxScroll > 0) {
				this.launcherScrollOffset = MathHelper.clamp(
						this.launcherScrollOffset - (int) Math.signum(verticalAmount), 0, maxScroll);
				return true;
			}
		}
		return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
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

		// Missile + USB slot backdrops (slots themselves are drawn by the screen handler).
		int missileSlotX = left + MissileLauncherScreenHandler.MISSILE_SLOT_X - 1;
		int missileSlotY = top + MissileLauncherScreenHandler.MISSILE_SLOT_Y - 1;
		context.fill(missileSlotX, missileSlotY, missileSlotX + 18, missileSlotY + 18, SLOT_COLOR);

		int usbSlotX = left + MissileLauncherScreenHandler.USB_SLOT_X - 1;
		int usbSlotY = top + MissileLauncherScreenHandler.USB_SLOT_Y - 1;
		context.fill(usbSlotX, usbSlotY, usbSlotX + 18, usbSlotY + 18, SLOT_COLOR);

		// Player inventory backdrop rows.
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
		// Custom layout, so we draw our own labels instead of the defaults.
		context.drawText(this.textRenderer, this.title, this.titleX, this.titleY, LABEL_COLOR, false);
		context.drawText(this.textRenderer, this.statusText, 10, STATUS_TEXT_Y, LABEL_COLOR, false);

		context.drawText(this.textRenderer,
				Text.translatable("gui.icbmbasics.saved_targets"), LIST_X, LAUNCHER_HEADER_Y, LABEL_COLOR, false);
		drawWaypointList(context, this.handler.getLauncherWaypoints(), this.launcherScrollOffset,
				LAUNCHER_LIST_Y, LAUNCHER_VISIBLE_ROWS, true);

		context.drawText(this.textRenderer,
				Text.translatable("gui.icbmbasics.drive_waypoints"), LIST_X, DRIVE_HEADER_Y, DRIVE_LABEL_COLOR, false);
		drawWaypointList(context, this.handler.getDriveWaypoints(), this.driveScrollOffset,
				DRIVE_LIST_Y, DRIVE_VISIBLE_ROWS, false);
	}

	private void drawWaypointList(DrawContext context, List<Waypoint> waypoints, int scrollOffset,
			int listY, int visibleRows, boolean deletable) {
		if (waypoints.isEmpty()) {
			context.drawText(this.textRenderer,
					Text.translatable("gui.icbmbasics.no_waypoints"), LIST_X, listY, LABEL_COLOR, false);
			return;
		}
		for (int i = 0; i < visibleRows; i++) {
			int index = scrollOffset + i;
			if (index >= waypoints.size()) {
				break;
			}
			Waypoint waypoint = waypoints.get(index);
			int rowY = listY + i * LIST_ROW_HEIGHT;
			String label = this.textRenderer.trimToWidth(waypoint.name(), DELETE_X - LIST_X - 4);
			context.drawText(this.textRenderer, Text.literal(label), LIST_X, rowY, LABEL_COLOR, false);
			if (deletable) {
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
		// While a coordinate/name field is focused, let it consume keys so typing
		// the inventory key ("E") doesn't close the screen.
		for (TextFieldWidget field : new TextFieldWidget[]{this.xField, this.yField, this.zField, this.nameField}) {
			if (field != null && field.isFocused()) {
				field.keyPressed(input);
				return true;
			}
		}
		return super.keyPressed(input);
	}
}
