package com.example.icbmbasics.block;

import com.example.icbmbasics.block.entity.MissileLauncherBlockEntity;
import com.mojang.serialization.MapCodec;

import net.minecraft.block.Block;
import net.minecraft.block.BlockRenderType;
import net.minecraft.block.BlockState;
import net.minecraft.block.BlockWithEntity;
import net.minecraft.util.BlockMirror;
import net.minecraft.util.BlockRotation;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.BooleanProperty;
import net.minecraft.state.property.EnumProperty;
import net.minecraft.state.property.IntProperty;
import net.minecraft.state.property.Properties;
import net.minecraft.util.ActionResult;
import net.minecraft.util.ItemScatterer;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;
import net.minecraft.world.block.WireOrientation;

import org.jetbrains.annotations.Nullable;

/**
 * The missile launcher. Faces the placer like a furnace/dispenser, opens a GUI
 * on right click, and fires on a redstone rising edge (e.g. a lever flick).
 */
public class MissileLauncherBlock extends BlockWithEntity {
	public static final MapCodec<MissileLauncherBlock> CODEC = createCodec(MissileLauncherBlock::new);

	public static final EnumProperty<Direction> FACING = Properties.HORIZONTAL_FACING;
	public static final BooleanProperty POWERED = Properties.POWERED;
	/**
	 * What's sitting on the pad: {@link #LOAD_EMPTY}, {@link #LOAD_STANDARD} or
	 * {@link #LOAD_CRUISE}. Drives which missile (if any) the platform model
	 * shows standing in its guard rails, kept in sync by
	 * {@code MissileLauncherBlockEntity.syncLoadedMissile()}.
	 *
	 * <p>An int rather than a {@code StringIdentifiable} enum purely for
	 * consistency - {@code ArmoredBlock.ARMOR_DAMAGE} and
	 * {@code SamSiteBlock.LOADED} are both ints, and the repo has no
	 * custom-enum-property precedent to follow. The cost is that F3 shows
	 * {@code loaded=2} rather than {@code loaded=cruise}.
	 */
	public static final IntProperty LOADED = IntProperty.of("loaded", 0, 2);

	public static final int LOAD_EMPTY = 0;
	public static final int LOAD_STANDARD = 1;
	public static final int LOAD_CRUISE = 2;

	public MissileLauncherBlock(Settings settings) {
		super(settings);
		this.setDefaultState(this.getDefaultState()
				.with(FACING, Direction.NORTH)
				.with(POWERED, false)
				.with(LOADED, LOAD_EMPTY));
	}

	@Override
	protected MapCodec<? extends BlockWithEntity> getCodec() {
		return CODEC;
	}

	@Override
	protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
		builder.add(FACING, POWERED, LOADED);
	}

	@Override
	@Nullable
	public BlockState getPlacementState(ItemPlacementContext ctx) {
		return this.getDefaultState().with(FACING, ctx.getHorizontalPlayerFacing().getOpposite());
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
		return new MissileLauncherBlockEntity(pos, state);
	}

	@Override
	protected ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, BlockHitResult hit) {
		if (!world.isClient() && world.getBlockEntity(pos) instanceof MissileLauncherBlockEntity launcher) {
			player.openHandledScreen(launcher);
		}
		return ActionResult.SUCCESS;
	}

	@Override
	protected void neighborUpdate(BlockState state, World world, BlockPos pos, Block sourceBlock,
			@Nullable WireOrientation wireOrientation, boolean notify) {
		if (world.isClient()) {
			return;
		}
		boolean powered = world.isReceivingRedstonePower(pos);
		if (powered != state.get(POWERED)) {
			world.setBlockState(pos, state.with(POWERED, powered), Block.NOTIFY_LISTENERS);
			// Fire only on the rising edge, like a dispenser.
			if (powered && world.getBlockEntity(pos) instanceof MissileLauncherBlockEntity launcher) {
				launcher.tryLaunch();
			}
		}
	}

	@Override
	protected void onStateReplaced(BlockState state, ServerWorld world, BlockPos pos, boolean moved) {
		if (world.getBlockEntity(pos) instanceof MissileLauncherBlockEntity launcher) {
			ItemScatterer.spawn(world, pos, launcher);
		}
		super.onStateReplaced(state, world, pos, moved);
	}
}
