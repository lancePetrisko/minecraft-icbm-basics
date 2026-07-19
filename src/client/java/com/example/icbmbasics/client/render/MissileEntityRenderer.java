package com.example.icbmbasics.client.render;

import net.minecraft.client.item.ItemModelManager;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.state.CameraRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.FlyingItemEntity;
import net.minecraft.item.ItemDisplayContext;
import net.minecraft.util.math.RotationAxis;

/**
 * Renders a {@link FlyingItemEntity} (missile, SAM interceptor) as its item,
 * oriented along the entity's own yaw/pitch instead of billboarding to the
 * camera like vanilla's {@code FlyingItemEntityRenderer} does - see
 * {@code MissileEntity#updateRotation}/{@code SamInterceptorEntity#tick} for
 * where that yaw/pitch comes from.
 */
public class MissileEntityRenderer<T extends Entity & FlyingItemEntity> extends EntityRenderer<T, MissileRenderState> {
	private final ItemModelManager itemModelManager;
	private final float scale;

	public MissileEntityRenderer(EntityRendererFactory.Context ctx, float scale) {
		super(ctx);
		this.itemModelManager = ctx.getItemModelManager();
		this.scale = scale;
	}

	@Override
	public MissileRenderState createRenderState() {
		return new MissileRenderState();
	}

	@Override
	public void updateRenderState(T entity, MissileRenderState state, float tickDelta) {
		super.updateRenderState(entity, state, tickDelta);
		this.itemModelManager.updateForNonLivingEntity(state.itemRenderState, entity.getStack(), ItemDisplayContext.GROUND, entity);
		state.yaw = entity.getYaw(tickDelta);
		state.pitch = entity.getPitch(tickDelta);
	}

	@Override
	public void render(MissileRenderState state, MatrixStack matrices, OrderedRenderCommandQueue queue, CameraRenderState camera) {
		matrices.push();
		matrices.scale(this.scale, this.scale, this.scale);
		// Nose-along-velocity instead of camera billboard: yaw about world Y,
		// then pitch about the entity's own (already-yawed) local X.
		matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(180.0f - state.yaw));
		matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(state.pitch));
		state.itemRenderState.render(matrices, queue, state.light, OverlayTexture.DEFAULT_UV, state.outlineColor);
		matrices.pop();
		super.render(state, matrices, queue, camera);
	}
}
