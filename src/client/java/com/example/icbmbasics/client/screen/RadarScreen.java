package com.example.icbmbasics.client.screen;

import java.util.List;

import com.example.icbmbasics.network.RadarContact;
import com.example.icbmbasics.network.RadarLogEntry;
import com.example.icbmbasics.screen.RadarScreenHandler;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;

/**
 * Classic scanning-circle radar scope: a rotating sweep line, blips for
 * tracked missiles (cyan = ours/outgoing, red = incoming/unidentified), and a
 * scrollable impact log below. Purely a display - no slots, no server writes.
 */
public class RadarScreen extends HandledScreen<RadarScreenHandler> {
	private static final int PANEL_COLOR = 0xFFC6C6C6;
	private static final int PANEL_BORDER_DARK = 0xFF555555;
	private static final int PANEL_BORDER_LIGHT = 0xFFFFFFFF;
	private static final int LABEL_COLOR = 0xFF404040;

	private static final int SCOPE_CENTER_Y = 88;
	private static final int SCOPE_RADIUS = 68;
	private static final int SCOPE_BG_COLOR = 0xFF001A0A;
	private static final int SCOPE_RING_COLOR = 0x40 << 24 | 0x00CC55;
	private static final int SWEEP_COLOR = 0xFF33FF88;
	private static final int OUTGOING_COLOR = 0xFF55DDFF;
	private static final int INCOMING_COLOR = 0xFFFF5555;
	/** Full sweep rotation period. */
	private static final long SWEEP_PERIOD_MS = 4000;

	private static final int LOG_HEADER_Y = 164;
	private static final int LOG_Y = 176;
	private static final int LOG_ROW_HEIGHT = 10;
	private static final int LOG_VISIBLE_ROWS = 4;

	private int scrollOffset;

	public RadarScreen(RadarScreenHandler handler, PlayerInventory inventory, Text title) {
		super(handler, inventory, title);
		this.backgroundWidth = 200;
		this.backgroundHeight = LOG_Y + LOG_VISIBLE_ROWS * LOG_ROW_HEIGHT + 10;
	}

	@Override
	protected void init() {
		super.init();
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
		int maxScroll = Math.max(0, this.handler.getLog().size() - LOG_VISIBLE_ROWS);
		if (maxScroll > 0) {
			this.scrollOffset = MathHelper.clamp(this.scrollOffset - (int) Math.signum(verticalAmount), 0, maxScroll);
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

		int cx = left + this.backgroundWidth / 2;
		int cy = top + SCOPE_CENTER_Y;
		this.drawScope(context, cx, cy);
	}

	private void drawScope(DrawContext context, int cx, int cy) {
		// Filled circle, scanline style.
		for (int dy = -SCOPE_RADIUS; dy <= SCOPE_RADIUS; dy++) {
			int halfWidth = (int) Math.sqrt((double) SCOPE_RADIUS * SCOPE_RADIUS - (double) dy * dy);
			context.fill(cx - halfWidth, cy + dy, cx + halfWidth, cy + dy + 1, SCOPE_BG_COLOR);
		}

		// Range rings at quarter, half, and full radius.
		for (int i = 1; i <= 3; i++) {
			drawRing(context, cx, cy, SCOPE_RADIUS * i / 3);
		}

		// Rotating sweep line.
		double angle = ((System.currentTimeMillis() % SWEEP_PERIOD_MS) / (double) SWEEP_PERIOD_MS) * Math.PI * 2.0;
		double dx = Math.cos(angle);
		double dy = Math.sin(angle);
		for (int r = 0; r <= SCOPE_RADIUS; r++) {
			int px = cx + (int) Math.round(dx * r);
			int py = cy + (int) Math.round(dy * r);
			context.fill(px, py, px + 1, py + 1, SWEEP_COLOR);
		}

		// Blips.
		BlockPos radarPos = this.handler.getRadarPos();
		int detectionRadius = this.handler.getDetectionRadius();
		double scale = detectionRadius > 0 ? (double) SCOPE_RADIUS / detectionRadius : 0.0;

		for (RadarContact contact : this.handler.getContacts()) {
			double wx = contact.x() - (radarPos.getX() + 0.5);
			double wz = contact.z() - (radarPos.getZ() + 0.5);
			double horizontalDist = Math.sqrt(wx * wx + wz * wz);
			if (horizontalDist > detectionRadius) {
				continue;
			}
			int bx = cx + (int) Math.round(wx * scale);
			int by = cy + (int) Math.round(wz * scale);
			int color = contact.outgoing() ? OUTGOING_COLOR : INCOMING_COLOR;
			context.fill(bx - 1, by - 1, bx + 2, by + 2, color);
		}
	}

	private void drawRing(DrawContext context, int cx, int cy, int radius) {
		int segments = Math.max(16, radius * 2);
		for (int i = 0; i < segments; i++) {
			double angle = (Math.PI * 2.0 * i) / segments;
			int px = cx + (int) Math.round(Math.cos(angle) * radius);
			int py = cy + (int) Math.round(Math.sin(angle) * radius);
			context.fill(px, py, px + 1, py + 1, SCOPE_RING_COLOR);
		}
	}

	@Override
	protected void drawForeground(DrawContext context, int mouseX, int mouseY) {
		context.drawText(this.textRenderer, this.title, this.titleX, this.titleY, LABEL_COLOR, false);

		context.drawText(this.textRenderer,
				Text.translatable("gui.icbmbasics.radar_log_header"), 10, LOG_HEADER_Y, LABEL_COLOR, false);

		List<RadarLogEntry> log = this.handler.getLog();
		if (log.isEmpty()) {
			context.drawText(this.textRenderer,
					Text.translatable("gui.icbmbasics.radar_log_empty"), 10, LOG_Y, LABEL_COLOR, false);
			return;
		}

		for (int i = 0; i < LOG_VISIBLE_ROWS; i++) {
			int index = this.scrollOffset + i;
			if (index >= log.size()) {
				break;
			}
			RadarLogEntry entry = log.get(index);
			int rowY = LOG_Y + i * LOG_ROW_HEIGHT;
			context.drawText(this.textRenderer,
					Text.translatable("gui.icbmbasics.radar_log_impact", entry.x(), entry.y(), entry.z()),
					10, rowY, LABEL_COLOR, false);
		}
	}

	@Override
	public void render(DrawContext context, int mouseX, int mouseY, float delta) {
		super.render(context, mouseX, mouseY, delta);
		this.drawMouseoverTooltip(context, mouseX, mouseY);
	}
}
