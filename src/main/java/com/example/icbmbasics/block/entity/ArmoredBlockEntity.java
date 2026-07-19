package com.example.icbmbasics.block.entity;

import com.example.icbmbasics.ICBMBasics;
import com.example.icbmbasics.block.ArmoredBlock;
import com.example.icbmbasics.registry.ModBlockEntities;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.storage.ReadView;
import net.minecraft.storage.WriteView;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;

/**
 * Tracks how many missile hits an {@link ArmoredBlock} has absorbed. Breaks
 * the block (no drops, like the missile crater carving) once its tier's hit
 * count is reached.
 */
public class ArmoredBlockEntity extends BlockEntity implements ArmoredEntity {
	private final int tier;
	private int hitsTaken;

	public ArmoredBlockEntity(BlockPos pos, BlockState state) {
		super(ModBlockEntities.ARMORED_BLOCK, pos, state);
		this.tier = state.getBlock() instanceof ArmoredBlock armoredBlock ? armoredBlock.getTier() : 1;
	}

	private int getMaxHits() {
		int[] tierHits = ICBMBasics.CONFIG.armorTierHits;
		return tierHits[MathHelper.clamp(this.tier - 1, 0, tierHits.length - 1)];
	}

	/** Called by a missile explosion within range. Advances the damage stage and breaks the block once absorbed. */
	@Override
	public void applyMissileHit() {
		if (!(this.getWorld() instanceof ServerWorld world)) {
			return;
		}

		this.hitsTaken++;
		int maxHits = this.getMaxHits();

		if (this.hitsTaken >= maxHits) {
			world.breakBlock(this.getPos(), false);
			return;
		}

		int stage = MathHelper.clamp(this.hitsTaken * 3 / maxHits, 0, 3);
		BlockState current = this.getCachedState();
		if (current.get(ArmoredBlock.ARMOR_DAMAGE) != stage) {
			world.setBlockState(this.getPos(), current.with(ArmoredBlock.ARMOR_DAMAGE, stage), Block.NOTIFY_LISTENERS);
		}
		this.markDirty();
	}

	@Override
	protected void writeData(WriteView view) {
		super.writeData(view);
		view.putInt("HitsTaken", this.hitsTaken);
	}

	@Override
	protected void readData(ReadView view) {
		super.readData(view);
		this.hitsTaken = view.getInt("HitsTaken", 0);
	}
}
