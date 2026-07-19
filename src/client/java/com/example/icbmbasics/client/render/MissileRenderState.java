package com.example.icbmbasics.client.render;

import net.minecraft.client.render.entity.state.FlyingItemEntityRenderState;

/**
 * {@link FlyingItemEntityRenderState} plus the lerped yaw/pitch
 * {@link MissileEntityRenderer} needs to orient the model with its flight
 * path - the vanilla state this extends has no rotation fields at all since
 * {@code FlyingItemEntityRenderer} always billboards to the camera instead.
 */
public class MissileRenderState extends FlyingItemEntityRenderState {
	public float yaw;
	public float pitch;
}
