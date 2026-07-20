package com.example.icbmbasics.client.render;

import net.minecraft.client.render.block.entity.state.BlockEntityRenderState;
import net.minecraft.util.math.Direction;

import org.jetbrains.annotations.Nullable;

/**
 * Per-frame render state for a monitor block: which way it faces, this
 * block's (row, col) slot within its wall group and the group's overall
 * (width, height) - computed each frame in
 * {@code MonitorBlockEntityRenderer#updateRenderState} via a flood fill over
 * same-facing neighboring monitors - plus the latest snapshot from its linked
 * radar, if any.
 */
public class MonitorRenderState extends BlockEntityRenderState {
	Direction facing = Direction.NORTH;
	int col;
	int row;
	int wallWidth = 1;
	int wallHeight = 1;
	@Nullable
	MonitorRenderData.Snapshot snapshot;
}
