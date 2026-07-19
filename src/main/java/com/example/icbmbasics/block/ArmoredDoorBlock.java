package com.example.icbmbasics.block;

import com.example.icbmbasics.ICBMBasics;
import com.example.icbmbasics.block.entity.ArmoredDoorBlockEntity;
import com.example.icbmbasics.storage.ArmorZoneStorage;

import net.minecraft.block.Block;
import net.minecraft.block.BlockEntityProvider;
import net.minecraft.block.BlockSetType;
import net.minecraft.block.BlockState;
import net.minecraft.block.DoorBlock;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.enums.DoubleBlockHalf;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.item.ItemStack;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.state.StateManager;
import net.minecraft.util.ActionResult;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.block.WireOrientation;

import org.jetbrains.annotations.Nullable;

/**
 * A tiered armored door: same missile-hit armor and zone-cap rules as
 * {@link ArmoredBlock}, plus a numeric codelock (see {@code ArmoredDoorBlockEntity}).
 * Extends vanilla {@link DoorBlock} to keep its two-tall placement, shapes,
 * and double-door rendering; only the interaction and redstone paths change.
 */
public class ArmoredDoorBlock extends DoorBlock implements BlockEntityProvider {
	private final int tier;

	public ArmoredDoorBlock(BlockSetType blockSetType, Settings settings, int tier) {
		super(blockSetType, settings);
		this.tier = tier;
		this.setDefaultState(this.getDefaultState().with(ArmoredBlock.ARMOR_DAMAGE, 0));
	}

	public int getTier() {
		return this.tier;
	}

	@Override
	protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
		super.appendProperties(builder);
		builder.add(ArmoredBlock.ARMOR_DAMAGE);
	}

	@Override
	@Nullable
	public BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
		// Only the lower half tracks armor/code - the upper half is purely visual.
		return state.get(HALF) == DoubleBlockHalf.LOWER ? new ArmoredDoorBlockEntity(pos, state) : null;
	}

	@Override
	@Nullable
	public BlockState getPlacementState(ItemPlacementContext ctx) {
		BlockState state = super.getPlacementState(ctx);
		if (state == null || !ArmorZoneStorage.checkPlacement(ctx)) {
			return null;
		}
		return state;
	}

	@Override
	public void onPlaced(World world, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack itemStack) {
		super.onPlaced(world, pos, state, placer, itemStack);
		if (world instanceof ServerWorld serverWorld) {
			ArmorZoneStorage.get(serverWorld).claim(pos, ICBMBasics.CONFIG.armorZoneRadius);
		}
	}

	@Override
	protected void onStateReplaced(BlockState state, ServerWorld world, BlockPos pos, boolean moved) {
		// Both halves fire this independently on removal; only release once, keyed
		// the same way claim() was (the lower half's position).
		if (state.get(HALF) == DoubleBlockHalf.LOWER) {
			ArmorZoneStorage.get(world).release(pos, ICBMBasics.CONFIG.armorZoneRadius);
		}
		super.onStateReplaced(state, world, pos, moved);
	}

	@Override
	protected ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, BlockHitResult hit) {
		if (world.isClient()) {
			return ActionResult.SUCCESS;
		}
		BlockPos lowerPos = state.get(HALF) == DoubleBlockHalf.LOWER ? pos : pos.down();
		if (world.getBlockEntity(lowerPos) instanceof ArmoredDoorBlockEntity doorEntity) {
			player.openHandledScreen(doorEntity);
		}
		return ActionResult.SUCCESS;
	}

	@Override
	protected void neighborUpdate(BlockState state, World world, BlockPos pos, Block sourceBlock,
			@Nullable WireOrientation wireOrientation, boolean notify) {
		// Locked armored doors ignore redstone entirely - only the keypad can open them.
	}
}
