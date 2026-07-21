package com.example.icbmbasics.block;

import com.mojang.serialization.MapCodec;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.BooleanProperty;
import net.minecraft.state.property.Properties;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.BlockView;

/**
 * A stationary redstone source, remotely fired by a {@code RemoteDetonatorItem}
 * linked to it via {@code ModComponents#DETONATOR_LINK} - the item never
 * needs a wire/proximity check to trigger it, unlike SAM sites/CIWS's own
 * radar link, since the whole point is detonating from anywhere in the world.
 * Emits full redstone power for {@link #PULSE_TICKS} once triggered (immediate
 * on, delayed off via a scheduled tick, same shape as vanilla's
 * {@code RedstoneLampBlock}), then goes quiet again - a single pulse per
 * button press, not a toggle.
 */
public class DetonatorChargeBlock extends Block {
	public static final MapCodec<DetonatorChargeBlock> CODEC = createCodec(DetonatorChargeBlock::new);

	public static final BooleanProperty POWERED = Properties.POWERED;
	private static final int PULSE_TICKS = 20;

	public DetonatorChargeBlock(Settings settings) {
		super(settings);
		this.setDefaultState(this.getDefaultState().with(POWERED, false));
	}

	@Override
	protected MapCodec<? extends Block> getCodec() {
		return CODEC;
	}

	@Override
	protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
		builder.add(POWERED);
	}

	/** Fires a single redstone pulse, re-checking that this block is still what's actually at {@code pos}. */
	public void trigger(ServerWorld world, BlockPos pos) {
		BlockState state = world.getBlockState(pos);
		if (!(state.getBlock() instanceof DetonatorChargeBlock) || state.get(POWERED)) {
			return;
		}
		world.setBlockState(pos, state.with(POWERED, true), Block.NOTIFY_ALL);
		world.scheduleBlockTick(pos, this, PULSE_TICKS);
		world.playSound(null, pos, SoundEvents.BLOCK_LEVER_CLICK, SoundCategory.BLOCKS, 1.0f, 0.6f);
	}

	@Override
	protected void scheduledTick(BlockState state, ServerWorld world, BlockPos pos, Random random) {
		if (state.get(POWERED)) {
			world.setBlockState(pos, state.with(POWERED, false), Block.NOTIFY_ALL);
		}
	}

	@Override
	protected boolean emitsRedstonePower(BlockState state) {
		return true;
	}

	@Override
	protected int getWeakRedstonePower(BlockState state, BlockView world, BlockPos pos, Direction direction) {
		return state.get(POWERED) ? 15 : 0;
	}

	@Override
	protected int getStrongRedstonePower(BlockState state, BlockView world, BlockPos pos, Direction direction) {
		return state.get(POWERED) ? 15 : 0;
	}
}
