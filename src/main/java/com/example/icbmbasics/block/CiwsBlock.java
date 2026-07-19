package com.example.icbmbasics.block;

import com.example.icbmbasics.block.entity.CiwsBlockEntity;
import com.example.icbmbasics.registry.ModBlockEntities;
import com.mojang.serialization.MapCodec;

import net.minecraft.block.BlockRenderType;
import net.minecraft.block.BlockState;
import net.minecraft.block.BlockWithEntity;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityTicker;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import org.jetbrains.annotations.Nullable;

/**
 * Ground-to-air defense: a close-in weapon system, short range but a fast
 * reload - see {@link CiwsBlockEntity#tick}. Same non-directional, no-GUI
 * shape as {@link SamSiteBlock}.
 */
public class CiwsBlock extends BlockWithEntity {
	public static final MapCodec<CiwsBlock> CODEC = createCodec(CiwsBlock::new);

	public CiwsBlock(Settings settings) {
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
		return new CiwsBlockEntity(pos, state);
	}

	@Override
	@Nullable
	@SuppressWarnings("unchecked")
	public <T extends BlockEntity> BlockEntityTicker<T> getTicker(World world, BlockState state, BlockEntityType<T> type) {
		if (world.isClient() || type != ModBlockEntities.CIWS) {
			return null;
		}
		return (BlockEntityTicker<T>) (BlockEntityTicker<CiwsBlockEntity>) CiwsBlockEntity::tick;
	}
}
