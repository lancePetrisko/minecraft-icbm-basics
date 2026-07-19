package com.example.icbmbasics.block;

import com.example.icbmbasics.block.entity.SamSiteBlockEntity;
import com.example.icbmbasics.registry.ModBlockEntities;
import com.mojang.serialization.MapCodec;

import net.minecraft.block.BlockRenderType;
import net.minecraft.block.BlockState;
import net.minecraft.block.BlockWithEntity;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityTicker;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.ActionResult;
import net.minecraft.util.ItemScatterer;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import org.jetbrains.annotations.Nullable;

/**
 * Ground-to-air defense: a stationary, non-directional block (like the radar)
 * that automatically launches interceptors at nearby missiles - see
 * {@link SamSiteBlockEntity#tick}. Right-clicking opens a small ammo GUI
 * (hoppers can feed {@code SAM_AMMO} into it too, same as any inventory).
 */
public class SamSiteBlock extends BlockWithEntity {
	public static final MapCodec<SamSiteBlock> CODEC = createCodec(SamSiteBlock::new);

	public SamSiteBlock(Settings settings) {
		super(settings);
	}

	@Override
	protected MapCodec<? extends BlockWithEntity> getCodec() {
		return CODEC;
	}

	@Override
	@Nullable
	public BlockState getPlacementState(ItemPlacementContext ctx) {
		return this.getDefaultState();
	}

	@Override
	protected BlockRenderType getRenderType(BlockState state) {
		return BlockRenderType.MODEL;
	}

	@Override
	@Nullable
	public BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
		return new SamSiteBlockEntity(pos, state);
	}

	@Override
	protected ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, BlockHitResult hit) {
		if (!world.isClient() && world.getBlockEntity(pos) instanceof SamSiteBlockEntity site) {
			player.openHandledScreen(site);
		}
		return ActionResult.SUCCESS;
	}

	@Override
	protected void onStateReplaced(BlockState state, ServerWorld world, BlockPos pos, boolean moved) {
		if (world.getBlockEntity(pos) instanceof SamSiteBlockEntity site) {
			ItemScatterer.spawn(world, pos, site);
		}
		super.onStateReplaced(state, world, pos, moved);
	}

	@Override
	@Nullable
	@SuppressWarnings("unchecked")
	public <T extends BlockEntity> BlockEntityTicker<T> getTicker(World world, BlockState state, BlockEntityType<T> type) {
		if (world.isClient() || type != ModBlockEntities.SAM_SITE) {
			return null;
		}
		return (BlockEntityTicker<T>) (BlockEntityTicker<SamSiteBlockEntity>) SamSiteBlockEntity::tick;
	}
}
