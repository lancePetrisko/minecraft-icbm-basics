package com.example.icbmbasics.block;

import com.example.icbmbasics.block.entity.MissileLauncherBlockEntity;
import com.mojang.serialization.MapCodec;

import net.minecraft.block.Block;
import net.minecraft.block.BlockRenderType;
import net.minecraft.block.BlockState;
import net.minecraft.block.BlockWithEntity;
import net.minecraft.util.BlockMirror;
import net.minecraft.util.BlockRotation;
import net.minecraft.block.Blocks;
import net.minecraft.block.ShapeContext;
import net.minecraft.block.enums.DoubleBlockHalf;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.random.Random;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.BlockView;
import net.minecraft.world.WorldView;
import net.minecraft.world.tick.ScheduledTickView;
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
	/** Pad (LOWER, owns the block entity) and gantry (UPPER, visual only). */
	public static final EnumProperty<DoubleBlockHalf> HALF = Properties.DOUBLE_BLOCK_HALF;
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
				.with(HALF, DoubleBlockHalf.LOWER)
				.with(POWERED, false)
				.with(LOADED, LOAD_EMPTY));
	}

	@Override
	protected MapCodec<? extends BlockWithEntity> getCodec() {
		return CODEC;
	}

	@Override
	protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
		builder.add(FACING, POWERED, LOADED, HALF);
	}

	@Override
	@Nullable
	public BlockState getPlacementState(ItemPlacementContext ctx) {
		BlockPos pos = ctx.getBlockPos();
		if (pos.getY() >= ctx.getWorld().getTopYInclusive()
				|| !ctx.getWorld().getBlockState(pos.up()).isReplaceable()) {
			return null;
		}
		return this.getDefaultState()
				.with(HALF, DoubleBlockHalf.LOWER)
				.with(FACING, ctx.getHorizontalPlayerFacing().getOpposite());
	}

	@Override
	public void onPlaced(World world, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack itemStack) {
		// TallBlockItem places the pad; the gantry above is ours to write.
		world.setBlockState(pos.up(), state.with(HALF, DoubleBlockHalf.UPPER), Block.NOTIFY_ALL);
	}

	@Override
	protected boolean canPlaceAt(BlockState state, WorldView world, BlockPos pos) {
		if (state.get(HALF) != DoubleBlockHalf.UPPER) {
			// Deliberately asymmetric, same as SamSiteBlock: launchers placed
			// while this was a one-block block exist in saves as lone lower
			// halves, and requiring a partner would pop every one of them.
			return true;
		}
		BlockState below = world.getBlockState(pos.down());
		return below.isOf(this) && below.get(HALF) == DoubleBlockHalf.LOWER;
	}

	@Override
	protected BlockState getStateForNeighborUpdate(BlockState state, WorldView world, ScheduledTickView tickView,
			BlockPos pos, Direction direction, BlockPos neighborPos, BlockState neighborState, Random random) {
		DoubleBlockHalf half = state.get(HALF);
		if (direction.getAxis() == Direction.Axis.Y && half.getOppositeDirection() == direction) {
			return neighborState.isOf(this) && neighborState.get(HALF) != half
					? state
					: Blocks.AIR.getDefaultState();
		}
		return super.getStateForNeighborUpdate(state, world, tickView, pos, direction, neighborPos, neighborState, random);
	}

	@Override
	public BlockState onBreak(World world, BlockPos pos, BlockState state, PlayerEntity player) {
		// Without this, breaking the gantry in creative leaves the pad to drop a
		// second launcher item when its own neighbour update removes it.
		if (!world.isClient() && player.isCreative() && state.get(HALF) == DoubleBlockHalf.UPPER) {
			BlockPos lower = pos.down();
			BlockState lowerState = world.getBlockState(lower);
			if (lowerState.isOf(this) && lowerState.get(HALF) == DoubleBlockHalf.LOWER) {
				world.setBlockState(lower, Blocks.AIR.getDefaultState(), Block.NOTIFY_ALL | Block.SKIP_DROPS);
			}
		}
		return super.onBreak(world, pos, state, player);
	}

	@Override
	protected BlockState rotate(BlockState state, BlockRotation rotation) {
		return state.with(FACING, rotation.rotate(state.get(FACING)));
	}

	@Override
	protected BlockState mirror(BlockState state, BlockMirror mirror) {
		return state.rotate(mirror.getRotation(state.get(FACING)));
	}

	/** Pad: full footprint but only as tall as the deck plus its rails. */
	private static final VoxelShape LOWER_SHAPE = Block.createCuboidShape(0.0, 0.0, 0.0, 16.0, 16.0, 16.0);
	/** Gantry: the tower and rails sweep most of the upper block. */
	private static final VoxelShape UPPER_SHAPE = VoxelShapes.fullCube();

	@Override
	protected VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
		return state.get(HALF) == DoubleBlockHalf.LOWER ? LOWER_SHAPE : UPPER_SHAPE;
	}

	@Override
	protected VoxelShape getCollisionShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
		return this.getOutlineShape(state, world, pos, context);
	}

	@Override
	protected BlockRenderType getRenderType(BlockState state) {
		return BlockRenderType.MODEL;
	}

	@Override
	@Nullable
	public BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
		// Only the pad holds the inventory/target - the gantry above is visual.
		return state.get(HALF) == DoubleBlockHalf.LOWER
				? new MissileLauncherBlockEntity(pos, state)
				: null;
	}

	/** The pad's position, whichever half was interacted with. */
	private static BlockPos lowerPos(BlockState state, BlockPos pos) {
		return state.get(HALF) == DoubleBlockHalf.LOWER ? pos : pos.down();
	}

	@Override
	protected ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, BlockHitResult hit) {
		if (!world.isClient()
				&& world.getBlockEntity(lowerPos(state, pos)) instanceof MissileLauncherBlockEntity launcher) {
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
		// Redstone against either half arms the launcher, but POWERED and the
		// firing edge are owned solely by the pad. Both halves converging on the
		// same lower-half state is what stops a lever touching both from firing
		// twice - the equality check below only passes once per edge.
		BlockPos lower = lowerPos(state, pos);
		BlockState lowerState = world.getBlockState(lower);
		if (!(lowerState.getBlock() instanceof MissileLauncherBlock)
				|| lowerState.get(HALF) != DoubleBlockHalf.LOWER) {
			return;
		}

		boolean powered = world.isReceivingRedstonePower(lower)
				|| world.isReceivingRedstonePower(lower.up());
		if (powered != lowerState.get(POWERED)) {
			world.setBlockState(lower, lowerState.with(POWERED, powered), Block.NOTIFY_LISTENERS);
			// Fire only on the rising edge, like a dispenser.
			if (powered && world.getBlockEntity(lower) instanceof MissileLauncherBlockEntity launcher) {
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
