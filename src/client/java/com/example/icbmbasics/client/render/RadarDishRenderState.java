package com.example.icbmbasics.client.render;

import net.minecraft.client.render.block.entity.state.BlockEntityRenderState;

/**
 * Per-frame render state for a radar's spinning dish. The only thing worth
 * carrying is a per-block phase offset: the spin itself is driven by
 * wall-clock time (see {@code RadarDishBlockEntityRenderer}), so without this
 * every radar in render distance would sweep in perfect lockstep and a row of
 * them would read as one mechanism rather than several.
 */
public class RadarDishRenderState extends BlockEntityRenderState {
	float phaseDegrees;
}
