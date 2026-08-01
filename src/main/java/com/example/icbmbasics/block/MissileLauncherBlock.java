package com.example.icbmbasics.block;

import com.example.icbmbasics.block.entity.MissileLauncherBlockEntity;
import com.mojang.serialization.MapCodec;

import net.minecraft.block.Block;
import net.minecraft.block.BlockRenderType;
import net.minecraft.block.BlockState;
import net.minecraft.block.BlockWithEntity;
import net.minecraft.block.Blocks;
import net.minecraft.block.ShapeContext;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.item.ItemStack;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.BooleanProperty;
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
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;
import net.minecraft.world.block.WireOrientation;

import org.jetbrains.annotations.Nullable;

/**
 * The missile launcher: a 2x2 footprint, 3 blocks tall, so twelve block
 * positions in all. Faces the placer like a furnace/dispenser, opens a GUI on
 * right click from any part, and fires on a redstone rising edge.
 *
 * <p><b>Why twelve real blocks and not one block with a big model.</b> A vanilla
 * block model may only use element coordinates in {@code [-16, 32]} - three
 * blocks on an axis, and only if the model is centred on the middle one. A
 * structure this size therefore cannot be one model, so the geometry is
 * authored once in "structure space" and sliced into one model per cell by
 * {@code scratchpad/launcher.py}. Regenerate that script's output rather than
 * hand-editing {@code models/block/missile_launcher_p*.json}.
 *
 * <p>The alternative - one block plus a {@code BlockEntityRenderer} - was
 * rejected: custom geometry in this MC version goes through
 * {@code submitCustom(..., RenderLayers.debugQuads(), ...)}, which is untextured
 * and unlit, so the whole gantry would need hand-faked shading (see
 * {@code RadarDishBlockEntityRenderer}). Slicing keeps normal block lighting,
 * culling and textures.
 */
public class MissileLauncherBlock extends BlockWithEntity {
	public static final MapCodec<MissileLauncherBlock> CODEC = createCodec(MissileLauncherBlock::new);

	public static final EnumProperty<Direction> FACING = Properties.HORIZONTAL_FACING;
	public static final BooleanProperty POWERED = Properties.POWERED;

	/**
	 * Which of the twelve cells this block is, indexed in the structure's own
	 * unrotated frame (FACING=NORTH, front = -Z):
	 * {@code part = y * 4 + z * 2 + x}, with x/z in 0..1 and y in 0..2.
	 * {@link #CORE_PART} (the bottom front-left cell) owns the block entity.
	 *
	 * <p>An int rather than three separate properties, and rather than a
	 * {@code StringIdentifiable} enum, for the same reason
	 * {@code SamSiteBlock.LOADED} and {@code ArmoredBlock.ARMOR_DAMAGE} are ints:
	 * the repo has no custom-enum-property precedent. Cost is that F3 shows
	 * {@code part=7} rather than something readable.
	 *
	 * <p><b>Must stay in sync with {@code part_index()} in
	 * {@code scratchpad/launcher.py}</b> - the generator names its models after
	 * this number.
	 */
	public static final IntProperty PART = IntProperty.of("part", 0, 11);

	/**
	 * What's sitting on the pad: {@link #LOAD_EMPTY}, {@link #LOAD_STANDARD} or
	 * {@link #LOAD_CRUISE}. Drives which missile (if any) the models show
	 * standing in the gantry, kept in sync by
	 * {@code MissileLauncherBlockEntity.syncLoadedMissile()} and mirrored onto
	 * all twelve parts - the missile crosses every one of them.
	 */
	public static final IntProperty LOADED = IntProperty.of("loaded", 0, 2);

	public static final int LOAD_EMPTY = 0;
	public static final int LOAD_STANDARD = 1;
	public static final int LOAD_CRUISE = 2;

	/** Bottom front-left cell. The only part with a block entity. */
	public static final int CORE_PART = 0;
	public static final int PART_COUNT = 12;

	private static final int CELLS_X = 2;
	private static final int CELLS_Y = 3;
	private static final int CELLS_Z = 2;

	/**
	 * Guards the sibling teardown in {@link #onStateReplaced} against recursing
	 * back through itself - removing part A removes part B, whose own
	 * {@code onStateReplaced} would otherwise try to remove A..L all over again.
	 * A plain field is enough: block updates run on the server thread.
	 */
	private static boolean dismantling = false;

	public MissileLauncherBlock(Settings settings) {
		super(settings);
		this.setDefaultState(this.getDefaultState()
				.with(FACING, Direction.NORTH)
				.with(PART, CORE_PART)
				.with(POWERED, false)
				.with(LOADED, LOAD_EMPTY));
	}

	@Override
	protected MapCodec<? extends BlockWithEntity> getCodec() {
		return CODEC;
	}

	@Override
	protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
		builder.add(FACING, POWERED, LOADED, PART);
	}

	// ------------------------------------------------------------- geometry

	private static int cellX(int part) {
		return part % CELLS_X;
	}

	private static int cellZ(int part) {
		return (part / CELLS_X) % CELLS_Z;
	}

	private static int cellY(int part) {
		return part / (CELLS_X * CELLS_Z);
	}

	/** Quarter turns clockwise (seen from above) that FACING applies. */
	private static int quarterTurns(Direction facing) {
		switch (facing) {
			case EAST:
				return 1;
			case SOUTH:
				return 2;
			case WEST:
				return 3;
			default:
				return 0;
		}
	}

	/**
	 * Rotates a footprint cell the same way the blockstate's {@code y} rotation
	 * rotates the model, so geometry and placement stay glued together.
	 *
	 * <p>A blockstate {@code y: 90} turns the model 90 degrees clockwise about
	 * the block centre, which in the structure's own 32-unit frame maps a point
	 * {@code (x, z)} to {@code (32 - z, x)}. Cell centres sit at
	 * {@code 16c + 8}, so that reduces to {@code (x, z) -> (1 - z, x)} on cell
	 * indices - the whole structure and each individual model turn together.
	 *
	 * @return {@code {x, z}}
	 */
	private static int[] rotateCell(int x, int z, int turns) {
		for (int i = 0; i < turns; i++) {
			int nx = 1 - z;
			z = x;
			x = nx;
		}
		return new int[]{x, z};
	}

	/** Where part {@code part} sits relative to the core, for a given facing. */
	public static BlockPos offsetFromCore(int part, Direction facing) {
		int turns = quarterTurns(facing);
		int[] cell = rotateCell(cellX(part), cellZ(part), turns);
		int[] core = rotateCell(cellX(CORE_PART), cellZ(CORE_PART), turns);
		return new BlockPos(cell[0] - core[0], cellY(part), cell[1] - core[1]);
	}

	/** The core's position, whichever part was interacted with. */
	public static BlockPos corePos(BlockState state, BlockPos pos) {
		if (!state.contains(PART) || !state.contains(FACING)) {
			return pos;
		}
		return pos.subtract(offsetFromCore(state.get(PART), state.get(FACING)));
	}

	/**
	 * The vertical axis the missile stands on: the corner shared by all four
	 * footprint cells, i.e. structure {@code x=16, z=16}. Derived from the
	 * footprint rather than hard-coded per facing so it cannot drift out of
	 * step with {@link #offsetFromCore}.
	 */
	public static Vec3d launchAxis(BlockPos core, Direction facing) {
		int minX = Integer.MAX_VALUE;
		int minZ = Integer.MAX_VALUE;
		for (int part = 0; part < CELLS_X * CELLS_Z; part++) {
			BlockPos off = offsetFromCore(part, facing);
			minX = Math.min(minX, off.getX());
			minZ = Math.min(minZ, off.getZ());
		}
		return new Vec3d(core.getX() + minX + 1.0, core.getY(), core.getZ() + minZ + 1.0);
	}

	// ------------------------------------------------------------ placement

	@Override
	@Nullable
	public BlockState getPlacementState(ItemPlacementContext ctx) {
		World world = ctx.getWorld();
		BlockPos core = ctx.getBlockPos();
		Direction facing = ctx.getHorizontalPlayerFacing().getOpposite();

		if (core.getY() + CELLS_Y - 1 > world.getTopYInclusive()) {
			return null;
		}
		for (int part = 0; part < PART_COUNT; part++) {
			if (part == CORE_PART) {
				continue;
			}
			BlockPos at = core.add(offsetFromCore(part, facing));
			if (!world.getBlockState(at).isReplaceable()) {
				return null;
			}
		}
		return this.getDefaultState().with(PART, CORE_PART).with(FACING, facing);
	}

	@Override
	public void onPlaced(World world, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack itemStack) {
		// The item places the core; the other eleven cells are ours to write.
		Direction facing = state.get(FACING);
		for (int part = 0; part < PART_COUNT; part++) {
			if (part == CORE_PART) {
				continue;
			}
			world.setBlockState(pos.add(offsetFromCore(part, facing)),
					state.with(PART, part), Block.NOTIFY_ALL);
		}
	}

	@Override
	protected BlockState rotate(BlockState state, BlockRotation rotation) {
		return state.with(FACING, rotation.rotate(state.get(FACING)));
	}

	@Override
	protected BlockState mirror(BlockState state, BlockMirror mirror) {
		return state.rotate(mirror.getRotation(state.get(FACING)));
	}

	/**
	 * Solid on every part. Per-cell shapes would have to be rotated in Java for
	 * all four facings (blockstate {@code y} rotates models, not
	 * {@code VoxelShape}s), which is a lot of machinery for a structure that
	 * reads as a solid launch tower anyway.
	 */
	@Override
	protected VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
		return VoxelShapes.fullCube();
	}

	@Override
	protected VoxelShape getCollisionShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
		return VoxelShapes.fullCube();
	}

	@Override
	protected BlockRenderType getRenderType(BlockState state) {
		return BlockRenderType.MODEL;
	}

	@Override
	@Nullable
	public BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
		// Only the core holds the inventory/target - the other eleven are visual.
		return state.get(PART) == CORE_PART ? new MissileLauncherBlockEntity(pos, state) : null;
	}

	// ------------------------------------------------------------ behaviour

	@Override
	protected ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, BlockHitResult hit) {
		if (!world.isClient()
				&& world.getBlockEntity(corePos(state, pos)) instanceof MissileLauncherBlockEntity launcher) {
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
		// Redstone against any part arms the launcher, but POWERED and the
		// firing edge are owned solely by the core. All twelve parts converging
		// on one state is what stops a lever touching two of them from firing
		// twice - the equality check below only passes once per edge.
		BlockPos core = corePos(state, pos);
		BlockState coreState = world.getBlockState(core);
		if (!(coreState.getBlock() instanceof MissileLauncherBlock)
				|| coreState.get(PART) != CORE_PART) {
			return;
		}

		Direction facing = coreState.get(FACING);
		boolean powered = false;
		for (int part = 0; part < PART_COUNT && !powered; part++) {
			powered = world.isReceivingRedstonePower(core.add(offsetFromCore(part, facing)));
		}
		if (powered != coreState.get(POWERED)) {
			world.setBlockState(core, coreState.with(POWERED, powered), Block.NOTIFY_LISTENERS);
			// Fire only on the rising edge, like a dispenser.
			if (powered && world.getBlockEntity(core) instanceof MissileLauncherBlockEntity launcher) {
				launcher.tryLaunch();
			}
		}
	}

	@Override
	protected void onStateReplaced(BlockState state, ServerWorld world, BlockPos pos, boolean moved) {
		if (world.getBlockEntity(pos) instanceof MissileLauncherBlockEntity launcher) {
			ItemScatterer.spawn(world, pos, launcher);
		}
		// Break one part, the whole structure comes down. Siblings go via
		// setBlockState rather than breakBlock so only the part the player
		// actually mined can drop an item.
		if (!moved && !dismantling && state.contains(PART) && state.contains(FACING)) {
			dismantling = true;
			try {
				BlockPos core = corePos(state, pos);
				Direction facing = state.get(FACING);
				for (int part = 0; part < PART_COUNT; part++) {
					BlockPos at = core.add(offsetFromCore(part, facing));
					if (at.equals(pos)) {
						continue;
					}
					BlockState sibling = world.getBlockState(at);
					if (sibling.getBlock() instanceof MissileLauncherBlock
							&& sibling.get(PART) == part
							&& sibling.get(FACING) == facing) {
						world.setBlockState(at, Blocks.AIR.getDefaultState(),
								Block.NOTIFY_ALL | Block.SKIP_DROPS);
					}
				}
			} finally {
				dismantling = false;
			}
		}
		super.onStateReplaced(state, world, pos, moved);
	}
}
