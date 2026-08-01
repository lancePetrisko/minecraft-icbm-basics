package com.example.icbmbasics.block;

import com.example.icbmbasics.block.entity.SamSiteBlockEntity;
import com.example.icbmbasics.registry.ModBlockEntities;
import com.mojang.serialization.MapCodec;

import net.minecraft.block.Block;
import net.minecraft.block.BlockRenderType;
import net.minecraft.block.BlockState;
import net.minecraft.block.BlockWithEntity;
import net.minecraft.block.Blocks;
import net.minecraft.block.ShapeContext;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityTicker;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.block.enums.DoubleBlockHalf;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.item.ItemStack;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.EnumProperty;
import net.minecraft.state.property.IntProperty;
import net.minecraft.state.property.Properties;
import net.minecraft.util.ActionResult;
import net.minecraft.util.BlockMirror;
import net.minecraft.util.BlockRotation;
import net.minecraft.util.ItemScatterer;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.random.Random;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;
import net.minecraft.world.WorldView;
import net.minecraft.world.tick.ScheduledTickView;

import org.jetbrains.annotations.Nullable;

/**
 * Ground-to-air defense that automatically launches interceptors at nearby
 * missiles - see {@link SamSiteBlockEntity#tick}. Right-clicking either half
 * opens a small ammo GUI (hoppers can feed {@code SAM_AMMO} into it too, same
 * as any inventory).
 *
 * <p><b>Two blocks tall.</b> The launcher is a base plinth ({@code HALF=LOWER})
 * with a tilted 6-tube rack sitting on it ({@code HALF=UPPER}), same
 * lower-half-owns-the-block-entity split as {@link ArmoredDoorBlock} - except
 * that block gets its two-tall placement free from vanilla {@code DoorBlock},
 * and this one extends {@link BlockWithEntity}, so placement, partner removal
 * and the creative-break double-drop guard are all implemented here.
 *
 * <p>Unlike the radar it is <b>directional</b> ({@code FACING}): the rack tilts
 * back 22.5 degrees, so which way it leans has to be part of the state.
 *
 * <p>Note the deliberate asymmetry in {@link #canPlaceAt}: an UPPER half
 * requires a LOWER below it, but a LOWER does <b>not</b> require an UPPER
 * above. Sites placed before this block became two-tall exist in the world as
 * lone lower halves, and requiring a partner would pop every one of them on
 * chunk load.
 */
public class SamSiteBlock extends BlockWithEntity {
	public static final MapCodec<SamSiteBlock> CODEC = createCodec(SamSiteBlock::new);

	public static final EnumProperty<DoubleBlockHalf> HALF = Properties.DOUBLE_BLOCK_HALF;
	public static final EnumProperty<Direction> FACING = Properties.HORIZONTAL_FACING;
	/**
	 * How many rockets are visibly loaded, 0 through
	 * {@code SamSiteBlockEntity.SLOT_COUNT} - drives which of the rack's tubes
	 * show a red nose cone. Same "discrete state picks a model variant" scheme
	 * as {@code ArmoredBlock.ARMOR_DAMAGE}, kept in sync by
	 * {@code SamSiteBlockEntity.syncLoadedTubes()}. Mirrored onto both halves
	 * even though only the rack's model reads it, so the two never disagree.
	 */
	public static final IntProperty LOADED = IntProperty.of("loaded", 0, SamSiteBlockEntity.SLOT_COUNT);

	/** Base plinth: narrower than a full cube, so its outriggers read as feet rather than a box. */
	private static final VoxelShape LOWER_SHAPE = Block.createCuboidShape(1.0, 0.0, 1.0, 15.0, 16.0, 15.0);
	/** Rack: the tilted tubes sweep most of the upper block, so a plain cube is both simpler and honest here. */
	private static final VoxelShape UPPER_SHAPE = VoxelShapes.fullCube();

	public SamSiteBlock(Settings settings) {
		super(settings);
		this.setDefaultState(this.getDefaultState()
				.with(HALF, DoubleBlockHalf.LOWER)
				.with(FACING, Direction.NORTH)
				.with(LOADED, 0));
	}

	@Override
	protected MapCodec<? extends BlockWithEntity> getCodec() {
		return CODEC;
	}

	@Override
	protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
		builder.add(HALF, FACING, LOADED);
	}

	// ---------------------------------------------------------------- placement

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
		// TallBlockItem places the lower half; the rack on top is ours to write.
		world.setBlockState(pos.up(), state.with(HALF, DoubleBlockHalf.UPPER), Block.NOTIFY_ALL);
	}

	@Override
	protected boolean canPlaceAt(BlockState state, WorldView world, BlockPos pos) {
		if (state.get(HALF) != DoubleBlockHalf.UPPER) {
			return true;
		}
		BlockState below = world.getBlockState(pos.down());
		return below.isOf(this) && below.get(HALF) == DoubleBlockHalf.LOWER;
	}

	@Override
	protected BlockState getStateForNeighborUpdate(BlockState state, WorldView world, ScheduledTickView tickView,
			BlockPos pos, Direction direction, BlockPos neighborPos, BlockState neighborState, Random random) {
		DoubleBlockHalf half = state.get(HALF);
		// Each half only cares about losing its own partner, so an unrelated
		// neighbour change never triggers the lookup.
		if (direction.getAxis() == Direction.Axis.Y && half.getOppositeDirection() == direction) {
			return neighborState.isOf(this) && neighborState.get(HALF) != half
					? state
					: Blocks.AIR.getDefaultState();
		}
		return super.getStateForNeighborUpdate(state, world, tickView, pos, direction, neighborPos, neighborState, random);
	}

	@Override
	public BlockState onBreak(World world, BlockPos pos, BlockState state, PlayerEntity player) {
		// Breaking the rack in creative would otherwise leave the base to drop a
		// second launcher item when its own neighbour update removes it.
		if (!world.isClient() && player.isCreative() && state.get(HALF) == DoubleBlockHalf.UPPER) {
			BlockPos lowerPos = pos.down();
			BlockState lowerState = world.getBlockState(lowerPos);
			if (lowerState.isOf(this) && lowerState.get(HALF) == DoubleBlockHalf.LOWER) {
				world.setBlockState(lowerPos, Blocks.AIR.getDefaultState(), Block.NOTIFY_ALL | Block.SKIP_DROPS);
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

	// ------------------------------------------------------------------- shapes

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

	// --------------------------------------------------------------- entity/gui

	@Override
	@Nullable
	public BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
		// Only the base tracks ammo/cooldown - the rack is purely visual.
		return state.get(HALF) == DoubleBlockHalf.LOWER ? new SamSiteBlockEntity(pos, state) : null;
	}

	@Override
	protected ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, BlockHitResult hit) {
		BlockPos lowerPos = state.get(HALF) == DoubleBlockHalf.LOWER ? pos : pos.down();
		if (!world.isClient() && world.getBlockEntity(lowerPos) instanceof SamSiteBlockEntity site) {
			player.openHandledScreen(site);
		}
		return ActionResult.SUCCESS;
	}

	@Override
	protected void onStateReplaced(BlockState state, ServerWorld world, BlockPos pos, boolean moved) {
		// Both halves fire this independently; only the lower one has an inventory
		// to scatter, so the getBlockEntity check is the guard.
		if (world.getBlockEntity(pos) instanceof SamSiteBlockEntity site) {
			ItemScatterer.spawn(world, pos, site);
		}
		super.onStateReplaced(state, world, pos, moved);
	}

	@Override
	@Nullable
	@SuppressWarnings("unchecked")
	public <T extends BlockEntity> BlockEntityTicker<T> getTicker(World world, BlockState state, BlockEntityType<T> type) {
		// The upper half has no block entity at all, so it must not get a ticker.
		if (world.isClient() || type != ModBlockEntities.SAM_SITE || state.get(HALF) != DoubleBlockHalf.LOWER) {
			return null;
		}
		return (BlockEntityTicker<T>) (BlockEntityTicker<SamSiteBlockEntity>) SamSiteBlockEntity::tick;
	}
}
