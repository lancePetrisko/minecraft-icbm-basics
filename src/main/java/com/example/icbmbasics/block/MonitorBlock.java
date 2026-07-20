package com.example.icbmbasics.block;

import com.example.icbmbasics.block.entity.MonitorBlockEntity;
import com.example.icbmbasics.registry.ModBlockEntities;
import com.mojang.serialization.MapCodec;

import net.minecraft.block.Block;
import net.minecraft.block.BlockRenderType;
import net.minecraft.block.BlockState;
import net.minecraft.block.BlockWithEntity;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityTicker;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.EnumProperty;
import net.minecraft.state.property.Properties;
import net.minecraft.util.BlockMirror;
import net.minecraft.util.BlockRotation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;

import org.jetbrains.annotations.Nullable;

/**
 * A wall-mounted display, purely passive (no GUI - see {@code MonitorBlockEntity}).
 * Any number of monitors placed adjacent to each other, facing the same way,
 * are treated as one big screen and stretch a single radar scope across the
 * whole group - see {@code client.render.MonitorBlockEntityRenderer} for the
 * flood-fill that discovers the group's shape client-side.
 *
 * <p>Faces away from whatever it was placed against (like a wall torch/sign),
 * not the player - right-clicking a floor/ceiling block falls back to facing
 * away from the placer, same as a dispenser.
 */
public class MonitorBlock extends BlockWithEntity {
	public static final MapCodec<MonitorBlock> CODEC = createCodec(MonitorBlock::new);

	public static final EnumProperty<Direction> FACING = Properties.HORIZONTAL_FACING;

	public MonitorBlock(Settings settings) {
		super(settings);
		this.setDefaultState(this.getDefaultState().with(FACING, Direction.NORTH));
	}

	@Override
	protected MapCodec<? extends BlockWithEntity> getCodec() {
		return CODEC;
	}

	@Override
	protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
		builder.add(FACING);
	}

	@Override
	@Nullable
	public BlockState getPlacementState(ItemPlacementContext ctx) {
		Direction side = ctx.getSide();
		Direction facing = side.getAxis() == Direction.Axis.Y
				? ctx.getHorizontalPlayerFacing().getOpposite()
				: side.getOpposite();
		return this.getDefaultState().with(FACING, facing);
	}

	@Override
	protected BlockState rotate(BlockState state, BlockRotation rotation) {
		return state.with(FACING, rotation.rotate(state.get(FACING)));
	}

	@Override
	protected BlockState mirror(BlockState state, BlockMirror mirror) {
		return state.rotate(mirror.getRotation(state.get(FACING)));
	}

	@Override
	protected BlockRenderType getRenderType(BlockState state) {
		return BlockRenderType.MODEL;
	}

	@Override
	@Nullable
	public BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
		return new MonitorBlockEntity(pos, state);
	}

	@Override
	@Nullable
	@SuppressWarnings("unchecked")
	public <T extends BlockEntity> BlockEntityTicker<T> getTicker(World world, BlockState state, BlockEntityType<T> type) {
		if (world.isClient() || type != ModBlockEntities.MONITOR) {
			return null;
		}
		return (BlockEntityTicker<T>) (BlockEntityTicker<MonitorBlockEntity>) MonitorBlockEntity::tick;
	}
}
