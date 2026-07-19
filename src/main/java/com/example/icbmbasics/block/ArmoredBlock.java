package com.example.icbmbasics.block;

import com.example.icbmbasics.ICBMBasics;
import com.example.icbmbasics.block.entity.ArmoredBlockEntity;
import com.example.icbmbasics.storage.ArmorZoneStorage;
import com.mojang.serialization.MapCodec;

import net.minecraft.block.Block;
import net.minecraft.block.BlockRenderType;
import net.minecraft.block.BlockState;
import net.minecraft.block.BlockWithEntity;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.item.ItemStack;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.IntProperty;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import org.jetbrains.annotations.Nullable;

/**
 * A tiered blast-resistant block that absorbs a set number of missile hits
 * (see {@code ArmoredBlockEntity}) before breaking. Placement is capped per
 * "armor zone" ({@link ArmorZoneStorage}) so players can't wall off unlimited
 * area with these - denied outright via {@link #getPlacementState}, before
 * the block or its entity ever exist, rather than placed-then-broken.
 */
public class ArmoredBlock extends BlockWithEntity {
	public static final MapCodec<ArmoredBlock> CODEC = createCodec(settings -> new ArmoredBlock(settings, 1));

	/** Shared damage-stage overlay (0 = undamaged, 3 = about to break) across every tier. */
	public static final IntProperty ARMOR_DAMAGE = IntProperty.of("armor_damage", 0, 3);

	private final int tier;

	public ArmoredBlock(Settings settings, int tier) {
		super(settings);
		this.tier = tier;
		this.setDefaultState(this.getDefaultState().with(ARMOR_DAMAGE, 0));
	}

	public int getTier() {
		return this.tier;
	}

	@Override
	protected MapCodec<? extends BlockWithEntity> getCodec() {
		return CODEC;
	}

	@Override
	protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
		builder.add(ARMOR_DAMAGE);
	}

	@Override
	protected BlockRenderType getRenderType(BlockState state) {
		return BlockRenderType.MODEL;
	}

	@Override
	@Nullable
	public BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
		return new ArmoredBlockEntity(pos, state);
	}

	@Override
	@Nullable
	public BlockState getPlacementState(ItemPlacementContext ctx) {
		if (!ArmorZoneStorage.checkPlacement(ctx)) {
			return null;
		}
		return this.getDefaultState();
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
		// The engine only calls this on genuine removal (not property-only state
		// changes like ARMOR_DAMAGE ticking up), so this always means "gone".
		// Every armored block that ever existed was successfully claimed at
		// placement time (getPlacementState refuses placement outright otherwise),
		// so releasing here is always paired with an earlier claim.
		ArmorZoneStorage.get(world).release(pos, ICBMBasics.CONFIG.armorZoneRadius);
		super.onStateReplaced(state, world, pos, moved);
	}
}
