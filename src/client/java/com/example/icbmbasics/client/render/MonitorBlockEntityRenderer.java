package com.example.icbmbasics.client.render;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import com.example.icbmbasics.block.MonitorBlock;
import com.example.icbmbasics.block.entity.MonitorBlockEntity;
import com.example.icbmbasics.network.RadarContact;

import net.minecraft.block.BlockState;
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
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

/**
 * Draws a monitor block as a slice of one shared, always-on radar scope
 * stretched across however many monitors are grouped together. Every monitor
 * independently flood-fills its own same-facing neighbors (see
 * {@link #computeWallLayout}) to find the group's overall width/height in
 * blocks and its own (row, col) slot in it, then renders only the fraction of
 * a shared virtual raster (see {@link #CELLS_PER_BLOCK}) that falls inside
 * its own block - so the circle/sweep/blips line up seamlessly across the
 * whole wall regardless of how many blocks make it up, without any block
 * needing to know about the others beyond its immediate neighbors.
 *
 * <p>Unlike {@code RadarScreen}'s GUI (a per-player HandledScreen), this is a
 * plain {@code BlockEntityRenderer} - it renders every frame for every nearby
 * player regardless of any GUI being open, which is what makes the sweep
 * "constant". Data comes from {@link MonitorRenderData}, refreshed by
 * {@code MonitorBlockEntity}'s own periodic S2C push - not gated behind a
 * screen the way the radar's own GUI update payload is.
 */
public class MonitorBlockEntityRenderer implements BlockEntityRenderer<MonitorBlockEntity, MonitorRenderState> {
	/** Caps the wall flood-fill, mirroring {@code WireNetwork}'s own node cap for the same reason. */
	private static final int MAX_WALL_NODES = 256;
	/** Virtual raster resolution per block face - higher is a smoother circle/sweep at the cost of more quads. */
	private static final int CELLS_PER_BLOCK = 24;
	private static final long SWEEP_PERIOD_MS = 4000;
	private static final float SWEEP_HALF_WIDTH_RAD = 0.12f;
	private static final float RING_THICKNESS_CELLS = 0.6f;
	/** How far the drawn screen plane sits proud of the block's own face, to avoid z-fighting with its model. */
	private static final double SCREEN_DEPTH = 0.502;

	private static final int BEZEL_COLOR = 0xFF050505;
	private static final int SCOPE_BG_COLOR = 0xFF001A0A;
	private static final int RING_COLOR = 0xFF0B5C33;
	private static final int SWEEP_COLOR = 0xFF33FF88;
	private static final int OUTGOING_COLOR = 0xFF55DDFF;
	private static final int INCOMING_COLOR = 0xFFFF5555;
	private static final int NO_SIGNAL_COLOR = 0xFF15181C;

	public MonitorBlockEntityRenderer(BlockEntityRendererFactory.Context ctx) {
	}

	@Override
	public MonitorRenderState createRenderState() {
		return new MonitorRenderState();
	}

	@Override
	public void updateRenderState(MonitorBlockEntity entity, MonitorRenderState state, float tickDelta,
			Vec3d cameraPos, ModelCommandRenderer.CrumblingOverlayCommand crumblingOverlay) {
		BlockEntityRenderState.updateBlockEntityRenderState(entity, state, crumblingOverlay);

		Direction facing = state.blockState.contains(MonitorBlock.FACING)
				? state.blockState.get(MonitorBlock.FACING)
				: Direction.NORTH;
		state.facing = facing;

		computeWallLayout(entity.getWorld(), entity.getPos(), facing, state);
		state.snapshot = MonitorRenderData.get(entity.getPos());
	}

	/** Flood-fills same-facing {@code MonitorBlock} neighbors in the wall's own plane to find this block's slot and the group's overall size. */
	private static void computeWallLayout(World world, BlockPos origin, Direction facing, MonitorRenderState state) {
		if (world == null) {
			state.col = 0;
			state.row = 0;
			state.wallWidth = 1;
			state.wallHeight = 1;
			return;
		}

		// up x facing (up is always +Y for a horizontal FACING) works out to the
		// same direction as rotateYCounterclockwise - see MonitorBlockEntityRenderer's
		// class doc for why "right" is defined this way.
		Direction right = facing.rotateYCounterclockwise();
		Direction[] planeDirs = { right, right.getOpposite(), Direction.UP, Direction.DOWN };

		Set<BlockPos> visited = new HashSet<>();
		Map<BlockPos, int[]> coords = new HashMap<>();
		Deque<BlockPos> queue = new ArrayDeque<>();
		visited.add(origin);
		coords.put(origin, new int[] { 0, 0 });
		queue.add(origin);

		int minCol = 0, maxCol = 0, minRow = 0, maxRow = 0;

		while (!queue.isEmpty() && visited.size() <= MAX_WALL_NODES) {
			BlockPos pos = queue.poll();
			int[] here = coords.get(pos);
			for (Direction dir : planeDirs) {
				BlockPos neighbor = pos.offset(dir);
				if (!visited.add(neighbor)) {
					continue;
				}
				if (!world.isChunkLoaded(neighbor)) {
					continue;
				}
				BlockState neighborState = world.getBlockState(neighbor);
				if (!(neighborState.getBlock() instanceof MonitorBlock) || neighborState.get(MonitorBlock.FACING) != facing) {
					continue;
				}
				int col = here[0] + (dir == right ? 1 : dir == right.getOpposite() ? -1 : 0);
				int row = here[1] + (dir == Direction.UP ? 1 : dir == Direction.DOWN ? -1 : 0);
				coords.put(neighbor, new int[] { col, row });
				minCol = Math.min(minCol, col);
				maxCol = Math.max(maxCol, col);
				minRow = Math.min(minRow, row);
				maxRow = Math.max(maxRow, row);
				queue.add(neighbor);
			}
		}

		int[] own = coords.get(origin);
		state.col = own[0] - minCol;
		state.row = own[1] - minRow;
		state.wallWidth = maxCol - minCol + 1;
		state.wallHeight = maxRow - minRow + 1;
	}

	@Override
	public void render(MonitorRenderState state, MatrixStack matrices, OrderedRenderCommandQueue queue, CameraRenderState camera) {
		Direction facing = state.facing;
		Direction right = facing.rotateYCounterclockwise();
		Vec3d rightVec = new Vec3d(right.getOffsetX(), right.getOffsetY(), right.getOffsetZ());
		Vec3d upVec = new Vec3d(0.0, 1.0, 0.0);
		Vec3d normalVec = new Vec3d(facing.getOffsetX(), facing.getOffsetY(), facing.getOffsetZ());

		int totalCellsX = state.wallWidth * CELLS_PER_BLOCK;
		int totalCellsY = state.wallHeight * CELLS_PER_BLOCK;
		int cellOriginX = state.col * CELLS_PER_BLOCK;
		int cellOriginY = state.row * CELLS_PER_BLOCK;
		double cellSize = 1.0 / CELLS_PER_BLOCK;

		double canvasCx = totalCellsX / 2.0;
		double canvasCy = totalCellsY / 2.0;
		double radius = Math.min(totalCellsX, totalCellsY) / 2.0 - 1.0;

		MonitorRenderData.Snapshot snapshot = state.snapshot;
		double sweepAngle = ((System.currentTimeMillis() % SWEEP_PERIOD_MS) / (double) SWEEP_PERIOD_MS) * Math.PI * 2.0;

		matrices.push();
		matrices.translate(0.5, 0.5, 0.5);

		queue.submitCustom(matrices, RenderLayers.debugQuads(), (matrixEntry, vertexConsumer) -> {
			for (int ly = 0; ly < CELLS_PER_BLOCK; ly++) {
				for (int lx = 0; lx < CELLS_PER_BLOCK; lx++) {
					int gx = cellOriginX + lx;
					int gy = cellOriginY + ly;

					int color;
					if (snapshot == null) {
						color = NO_SIGNAL_COLOR;
					} else {
						double dx = gx + 0.5 - canvasCx;
						double dy = gy + 0.5 - canvasCy;
						double dist = Math.sqrt(dx * dx + dy * dy);
						if (dist > radius) {
							color = BEZEL_COLOR;
						} else {
							color = SCOPE_BG_COLOR;
							for (int r = 1; r <= 3; r++) {
								if (Math.abs(dist - radius * r / 3.0) < RING_THICKNESS_CELLS) {
									color = RING_COLOR;
								}
							}
							double angle = Math.atan2(dy, dx);
							if (angularDistance(angle, sweepAngle) < SWEEP_HALF_WIDTH_RAD) {
								color = SWEEP_COLOR;
							}
						}
					}

					double u0 = -0.5 + lx * cellSize;
					double v0 = -0.5 + ly * cellSize;
					emitQuad(vertexConsumer, matrixEntry, rightVec, upVec, normalVec, u0, v0, cellSize, color);
				}
			}

			if (snapshot != null) {
				double scale = snapshot.detectionRadius() > 0 ? radius / snapshot.detectionRadius() : 0.0;
				for (RadarContact contact : snapshot.contacts()) {
					double wx = contact.x() - (snapshot.radarPos().getX() + 0.5);
					double wz = contact.z() - (snapshot.radarPos().getZ() + 0.5);
					if (Math.sqrt(wx * wx + wz * wz) > snapshot.detectionRadius()) {
						continue;
					}
					double blipGx = canvasCx + wx * scale;
					double blipGy = canvasCy + wz * scale;
					if (blipGx < cellOriginX || blipGx >= cellOriginX + CELLS_PER_BLOCK
							|| blipGy < cellOriginY || blipGy >= cellOriginY + CELLS_PER_BLOCK) {
						continue;
					}
					double localU = -0.5 + (blipGx - cellOriginX) * cellSize;
					double localV = -0.5 + (blipGy - cellOriginY) * cellSize;
					int color = contact.outgoing() ? OUTGOING_COLOR : INCOMING_COLOR;
					double blipSize = cellSize * 2.5;
					emitQuad(vertexConsumer, matrixEntry, rightVec, upVec, normalVec,
							localU - blipSize / 2.0, localV - blipSize / 2.0, blipSize, color);
				}
			}
		});

		matrices.pop();
	}

	private static double angularDistance(double a, double b) {
		double diff = Math.abs(a - b) % (Math.PI * 2.0);
		return Math.min(diff, Math.PI * 2.0 - diff);
	}

	/**
	 * Emits one quad in the plane spanned by {@code rightVec}/{@code upVec}, at
	 * local (u, v)..(u+size, v+size) relative to the block's own center,
	 * pushed {@link #SCREEN_DEPTH} along {@code normalVec} so it sits just
	 * outside the block's own face instead of z-fighting with it.
	 */
	private static void emitQuad(VertexConsumer vertexConsumer, MatrixStack.Entry entry,
			Vec3d rightVec, Vec3d upVec, Vec3d normalVec, double u, double v, double size, int color) {
		int a = (color >>> 24) & 0xFF;
		int r = (color >>> 16) & 0xFF;
		int g = (color >>> 8) & 0xFF;
		int b = color & 0xFF;

		Vec3d base = normalVec.multiply(SCREEN_DEPTH);
		Vec3d p00 = base.add(rightVec.multiply(u)).add(upVec.multiply(v));
		Vec3d p10 = base.add(rightVec.multiply(u + size)).add(upVec.multiply(v));
		Vec3d p11 = base.add(rightVec.multiply(u + size)).add(upVec.multiply(v + size));
		Vec3d p01 = base.add(rightVec.multiply(u)).add(upVec.multiply(v + size));

		vertexConsumer.vertex(entry, (float) p00.x, (float) p00.y, (float) p00.z).color(r, g, b, a);
		vertexConsumer.vertex(entry, (float) p10.x, (float) p10.y, (float) p10.z).color(r, g, b, a);
		vertexConsumer.vertex(entry, (float) p11.x, (float) p11.y, (float) p11.z).color(r, g, b, a);
		vertexConsumer.vertex(entry, (float) p01.x, (float) p01.y, (float) p01.z).color(r, g, b, a);
	}
}
