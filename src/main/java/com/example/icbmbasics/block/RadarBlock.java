package com.example.icbmbasics.block;

import com.example.icbmbasics.block.entity.RadarBlockEntity;
import com.example.icbmbasics.registry.ModBlockEntities;
import com.mojang.serialization.MapCodec;

import net.minecraft.block.Block;
import net.minecraft.block.BlockRenderType;
import net.minecraft.block.BlockState;
import net.minecraft.block.BlockWithEntity;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityTicker;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.util.ActionResult;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import org.jetbrains.annotations.Nullable;

/**
 * The radar. A stationary, non-directional block (no facing needed - it scans
 * in every direction) that opens a scope GUI on right-click. All contact
 * scanning happens server-side in {@link RadarBlockEntity#tick}.
 */
public class RadarBlock extends BlockWithEntity {
	public static final MapCodec<RadarBlock> CODEC = createCodec(settings -> new RadarBlock(settings, 1));

	private final int tier;

	public RadarBlock(Settings settings, int tier) {
		super(settings);
		this.tier = tier;
	}

	public int getTier() {
		return this.tier;
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
		return new RadarBlockEntity(pos, state);
	}

	@Override
	protected ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, BlockHitResult hit) {
		if (!world.isClient() && world.getBlockEntity(pos) instanceof RadarBlockEntity radar) {
			player.openHandledScreen(radar);
		}
		return ActionResult.SUCCESS;
	}

	@Override
	@Nullable
	@SuppressWarnings("unchecked")
	public <T extends BlockEntity> BlockEntityTicker<T> getTicker(World world, BlockState state, BlockEntityType<T> type) {
		if (world.isClient() || type != ModBlockEntities.RADAR) {
			return null;
		}
		return (BlockEntityTicker<T>) (BlockEntityTicker<RadarBlockEntity>) RadarBlockEntity::tick;
	}
}
