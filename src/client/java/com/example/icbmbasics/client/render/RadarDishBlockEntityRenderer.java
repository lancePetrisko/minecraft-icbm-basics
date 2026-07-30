package com.example.icbmbasics.client.render;

import com.example.icbmbasics.block.entity.RadarBlockEntity;

import net.minecraft.client.render.RenderLayers;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.block.entity.BlockEntityRenderer;
import net.minecraft.client.render.block.entity.BlockEntityRendererFactory;
import net.minecraft.client.render.block.entity.state.BlockEntityRenderState;
import net.minecraft.client.render.command.ModelCommandRenderer;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.state.CameraRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;

/**
 * Draws the radar's parabolic dish, slowly rotating, on top of the static mast
 * in {@code models/block/radar_mk1.json}. The dish lives here rather than in
 * that model because a vanilla block model can't animate - and it can't
 * express a paraboloid either, only axis-aligned boxes at a handful of legal
 * rotations.
 *
 * <p>The spin is driven by {@link System#currentTimeMillis()}, not by server
 * ticks or any block-entity field, exactly like {@code RadarScreen}'s and
 * {@code MonitorBlockEntityRenderer}'s scope sweeps - it's decoration, so it
 * needs no state sync, and it keeps turning whether or not anyone has the
 * radar's GUI open (that {@code viewers} gate only governs contact scanning,
 * not this).
 *
 * <p>Geometry is emitted as raw colored quads through
 * {@code OrderedRenderCommandQueue.submitCustom} on {@code RenderLayers
 * .debugQuads()} - the same untextured path the monitor wall uses. That layer
 * is unlit, so the shading here is faked: each quad's color is scaled by its
 * own normal against a fixed light direction, with the normal rotated by the
 * dish's current tilt/spin first so the highlight stays put in the world
 * instead of turning with the dish.
 */
public class RadarDishBlockEntityRenderer implements BlockEntityRenderer<RadarBlockEntity, RadarDishRenderState> {
	/** One revolution per this many ms. Deliberately slow - a search radar sweep, not a fan. */
	private static final long SPIN_PERIOD_MS = 12000;
	/** Sectors around the axis / rings out from the vertex. 16x4 is smooth enough at block scale. */
	private static final int SECTORS = 16;
	private static final int RINGS = 4;
	/**
	 * Blocks. Has to stay under 0.5 or the rim sweeps outside the block and
	 * clips whatever is built next to the radar as it turns.
	 */
	private static final double DISH_RADIUS = 0.42;
	private static final double DISH_DEPTH = 0.17;
	/** Dish thickness - the gap between the concave shell and the convex backing shell. */
	private static final double SHELL = 0.014;
	/** Height of the dish's vertex, just clear of the mast collar's top (model y 10.4/16). */
	private static final double PIVOT_Y = 0.70;
	/** Tilted up off vertical so the bowl faces the sky at an angle rather than straight up. */
	private static final float TILT_DEGREES = -35.0f;

	private static final int FACE_COLOR = 0xFFC4C7CA;
	private static final int BACK_COLOR = 0xFF74787D;
	private static final int RIM_COLOR = 0xFF9AA0A6;
	private static final int HORN_COLOR = 0xFF3A3D40;

	private static final Vec3d LIGHT = new Vec3d(-0.35, 0.9, -0.25).normalize();

	public RadarDishBlockEntityRenderer(BlockEntityRendererFactory.Context ctx) {
	}

	@Override
	public RadarDishRenderState createRenderState() {
		return new RadarDishRenderState();
	}

	@Override
	public void updateRenderState(RadarBlockEntity entity, RadarDishRenderState state, float tickDelta,
			Vec3d cameraPos, ModelCommandRenderer.CrumblingOverlayCommand crumblingOverlay) {
		BlockEntityRenderState.updateBlockEntityRenderState(entity, state, crumblingOverlay);
		state.phaseDegrees = phaseFor(entity.getPos());
	}

	/** A stable per-position offset so neighboring radars don't sweep in lockstep. */
	private static float phaseFor(BlockPos pos) {
		int hash = pos.getX() * 31 + pos.getY() * 17 + pos.getZ() * 13;
		return Math.floorMod(hash, 360);
	}

	@Override
	public void render(RadarDishRenderState state, MatrixStack matrices, OrderedRenderCommandQueue queue,
			CameraRenderState camera) {
		float spin = (System.currentTimeMillis() % SPIN_PERIOD_MS) / (float) SPIN_PERIOD_MS * 360.0f
				+ state.phaseDegrees;

		matrices.push();
		matrices.translate(0.5, PIVOT_Y, 0.5);
		matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(spin));
		matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(TILT_DEGREES));

		queue.submitCustom(matrices, RenderLayers.debugQuads(), (matrixEntry, vertexConsumer) -> {
			emitDish(vertexConsumer, matrixEntry, spin);
			// Feed horn at the paraboloid's focus, on a stalk from the vertex.
			// f = R^2 / (4 * depth) for y = depth * (r/R)^2.
			double focus = DISH_RADIUS * DISH_RADIUS / (4.0 * DISH_DEPTH);
			emitBox(vertexConsumer, matrixEntry, spin, 0.022, 0.02, focus * 0.86, 0.022, HORN_COLOR);
			emitBox(vertexConsumer, matrixEntry, spin, 0.055, focus * 0.86, focus * 1.1, 0.055, HORN_COLOR);
		});

		matrices.pop();
	}

	/** Concave shell, convex backing shell and the rim joining them, in dish-local coords (axis = +Y). */
	private static void emitDish(VertexConsumer vertexConsumer, MatrixStack.Entry entry, float spin) {
		Vec3d[][] front = new Vec3d[RINGS + 1][SECTORS + 1];
		Vec3d[][] back = new Vec3d[RINGS + 1][SECTORS + 1];
		for (int i = 0; i <= RINGS; i++) {
			double r = DISH_RADIUS * i / (double) RINGS;
			double y = DISH_DEPTH * (r / DISH_RADIUS) * (r / DISH_RADIUS);
			for (int j = 0; j <= SECTORS; j++) {
				double a = j * 2.0 * Math.PI / SECTORS;
				double x = r * Math.cos(a);
				double z = r * Math.sin(a);
				front[i][j] = new Vec3d(x, y, z);
				back[i][j] = new Vec3d(x, y - SHELL, z);
			}
		}

		for (int i = 0; i < RINGS; i++) {
			for (int j = 0; j < SECTORS; j++) {
				// Wound the opposite way from the backing shell so each is
				// front-facing from its own side, making the dish solid from
				// every angle without relying on the layer's cull mode.
				quad(vertexConsumer, entry, spin,
						front[i][j], front[i][j + 1], front[i + 1][j + 1], front[i + 1][j], FACE_COLOR);
				quad(vertexConsumer, entry, spin,
						back[i][j], back[i + 1][j], back[i + 1][j + 1], back[i][j + 1], BACK_COLOR);
			}
		}
		for (int j = 0; j < SECTORS; j++) {
			quad(vertexConsumer, entry, spin,
					front[RINGS][j], back[RINGS][j], back[RINGS][j + 1], front[RINGS][j + 1], RIM_COLOR);
		}
	}

	/** Axis-aligned box in dish-local coords, centered on the dish axis. */
	private static void emitBox(VertexConsumer vertexConsumer, MatrixStack.Entry entry, float spin,
			double halfX, double y0, double y1, double halfZ, int color) {
		Vec3d a = new Vec3d(-halfX, y0, -halfZ);
		Vec3d b = new Vec3d(halfX, y0, -halfZ);
		Vec3d c = new Vec3d(halfX, y0, halfZ);
		Vec3d d = new Vec3d(-halfX, y0, halfZ);
		Vec3d e = new Vec3d(-halfX, y1, -halfZ);
		Vec3d f = new Vec3d(halfX, y1, -halfZ);
		Vec3d g = new Vec3d(halfX, y1, halfZ);
		Vec3d h = new Vec3d(-halfX, y1, halfZ);
		quad(vertexConsumer, entry, spin, e, f, g, h, color);   // top
		quad(vertexConsumer, entry, spin, a, d, c, b, color);   // bottom
		quad(vertexConsumer, entry, spin, a, b, f, e, color);   // -Z
		quad(vertexConsumer, entry, spin, c, d, h, g, color);   // +Z
		quad(vertexConsumer, entry, spin, d, a, e, h, color);   // -X
		quad(vertexConsumer, entry, spin, b, c, g, f, color);   // +X
	}

	/**
	 * Emits one quad, shading {@code color} by this quad's own normal against
	 * {@link #LIGHT}. The normal is rotated out of dish-local space by the
	 * current tilt and spin first, so the lighting belongs to the world and
	 * doesn't rotate along with the dish.
	 */
	private static void quad(VertexConsumer vertexConsumer, MatrixStack.Entry entry, float spin,
			Vec3d p0, Vec3d p1, Vec3d p2, Vec3d p3, int color) {
		Vec3d normal = p1.subtract(p0).crossProduct(p2.subtract(p1));
		double length = normal.length();
		float shade = 1.0f;
		if (length > 1.0e-6) {
			Vec3d world = toWorld(normal.multiply(1.0 / length), spin);
			shade = (float) (0.62 + 0.38 * Math.max(0.0, world.dotProduct(LIGHT)));
		}

		int a = (color >>> 24) & 0xFF;
		int r = Math.round(((color >>> 16) & 0xFF) * shade);
		int g = Math.round(((color >>> 8) & 0xFF) * shade);
		int b = Math.round((color & 0xFF) * shade);

		vertexConsumer.vertex(entry, (float) p0.x, (float) p0.y, (float) p0.z).color(r, g, b, a);
		vertexConsumer.vertex(entry, (float) p1.x, (float) p1.y, (float) p1.z).color(r, g, b, a);
		vertexConsumer.vertex(entry, (float) p2.x, (float) p2.y, (float) p2.z).color(r, g, b, a);
		vertexConsumer.vertex(entry, (float) p3.x, (float) p3.y, (float) p3.z).color(r, g, b, a);
	}

	/** Dish-local -> world direction: the same tilt-then-spin the matrix stack applies. */
	private static Vec3d toWorld(Vec3d local, float spin) {
		double tilt = Math.toRadians(TILT_DEGREES);
		double ct = Math.cos(tilt);
		double st = Math.sin(tilt);
		double y = local.y * ct - local.z * st;
		double z = local.y * st + local.z * ct;
		double yaw = Math.toRadians(spin);
		double cy = Math.cos(yaw);
		double sy = Math.sin(yaw);
		return new Vec3d(local.x * cy + z * sy, y, -local.x * sy + z * cy);
	}
}
