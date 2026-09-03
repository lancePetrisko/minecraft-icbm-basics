package com.example.icbmbasics.block.entity;

import com.example.icbmbasics.ICBMBasics;
import com.example.icbmbasics.block.ArmoredBlock;
import com.example.icbmbasics.block.ArmoredDoorBlock;
import com.example.icbmbasics.network.ArmoredDoorScreenData;
import com.example.icbmbasics.registry.ModBlockEntities;
import com.example.icbmbasics.screen.ArmoredDoorScreenHandler;

import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerFactory;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.storage.ReadView;
import net.minecraft.storage.WriteView;
import net.minecraft.text.Text;
import net.minecraft.util.Uuids;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;

import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Lower-half-only state for an {@link ArmoredDoorBlock}: missile-hit armor
 * (same tier scheme as {@link ArmoredBlockEntity}) plus a numeric codelock.
 * The code is set on first use of the keypad and never client-trusted after
 * that - every submission is re-validated server-side.
 *
 * <p>Whoever sets the code becomes the {@link #owner} and is immediately
 * remembered; anyone who later submits the correct code is remembered too
 * (see {@link #authorizedPlayers}), so a remembered player's plain right-click
 * opens the door straight away instead of showing the keypad again (see
 * {@code ArmoredDoorBlock#onUse}). Only the owner can {@link #resetCode(UUID)}.
 */
public class ArmoredDoorBlockEntity extends BlockEntity
		implements ArmoredEntity, ExtendedScreenHandlerFactory<ArmoredDoorScreenData> {

	private final int tier;
	private int hitsTaken;
	private boolean codeSet;
	private int code;
	@Nullable
	private UUID owner;
	private final Set<UUID> authorizedPlayers = new HashSet<>();

	public ArmoredDoorBlockEntity(BlockPos pos, BlockState state) {
		super(ModBlockEntities.ARMORED_DOOR, pos, state);
		this.tier = state.getBlock() instanceof ArmoredDoorBlock doorBlock ? doorBlock.getTier() : 1;
	}

	public boolean isCodeSet() {
		return this.codeSet;
	}

	/** First-time setup only; does nothing if a code is already set. Becomes the door's owner and is remembered immediately. */
	public void setCode(int code, UUID playerId) {
		if (!this.codeSet) {
			this.code = code;
			this.codeSet = true;
			this.owner = playerId;
			this.authorizedPlayers.clear();
			this.authorizedPlayers.add(playerId);
			this.markDirty();
		}
	}

	/** Re-validates a submitted code; on success the caller is remembered so they aren't asked again. */
	public boolean checkCode(int candidate, UUID playerId) {
		if (this.codeSet && this.code == candidate) {
			if (this.authorizedPlayers.add(playerId)) {
				this.markDirty();
			}
			return true;
		}
		return false;
	}

	/** Whether this player already proved the code (or set it) and can open the door without re-entering it. */
	public boolean isAuthorized(UUID playerId) {
		return this.codeSet && this.authorizedPlayers.contains(playerId);
	}

	/** Whether this player set the current code - the only one allowed to reset it. */
	public boolean isOwner(UUID playerId) {
		return this.codeSet && playerId.equals(this.owner);
	}

	/** Clears the code, owner, and every remembered player so the door can be recoded. Owner-only; returns whether it actually reset. */
	public boolean resetCode(UUID playerId) {
		if (!this.isOwner(playerId)) {
			return false;
		}
		this.codeSet = false;
		this.code = 0;
		this.owner = null;
		this.authorizedPlayers.clear();
		this.markDirty();
		return true;
	}

	private int getMaxHits() {
		int[] tierHits = ICBMBasics.CONFIG.armorTierHits;
		return tierHits[MathHelper.clamp(this.tier - 1, 0, tierHits.length - 1)];
	}

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
		BlockState lower = this.getCachedState();
		if (lower.get(ArmoredBlock.ARMOR_DAMAGE) != stage) {
			world.setBlockState(this.getPos(), lower.with(ArmoredBlock.ARMOR_DAMAGE, stage), Block.NOTIFY_LISTENERS);

			// Mirror the damage stage onto the upper half so the whole door looks damaged.
			BlockPos upperPos = this.getPos().up();
			BlockState upper = world.getBlockState(upperPos);
			if (upper.isOf(lower.getBlock())) {
				world.setBlockState(upperPos, upper.with(ArmoredBlock.ARMOR_DAMAGE, stage), Block.NOTIFY_LISTENERS);
			}
		}
		this.markDirty();
	}

	@Override
	public Text getDisplayName() {
		return Text.translatable("block.icbmbasics.armored_door_mk" + this.tier);
	}

	@Override
	@Nullable
	public ScreenHandler createMenu(int syncId, PlayerInventory playerInventory, PlayerEntity player) {
		return new ArmoredDoorScreenHandler(syncId, this, this.getPos(), player);
	}

	@Override
	public ArmoredDoorScreenData getScreenOpeningData(ServerPlayerEntity player) {
		UUID playerId = player.getUuid();
		return new ArmoredDoorScreenData(this.getPos(), this.codeSet, this.isOwner(playerId), this.isAuthorized(playerId));
	}

	@Override
	protected void writeData(WriteView view) {
		super.writeData(view);
		view.putInt("HitsTaken", this.hitsTaken);
		view.putBoolean("CodeSet", this.codeSet);
		view.putInt("Code", this.code);
		view.putNullable("Owner", Uuids.CODEC, this.owner);
		view.put("AuthorizedPlayers", Uuids.SET_CODEC, this.authorizedPlayers);
	}

	@Override
	protected void readData(ReadView view) {
		super.readData(view);
		this.hitsTaken = view.getInt("HitsTaken", 0);
		this.codeSet = view.getBoolean("CodeSet", false);
		this.code = view.getInt("Code", 0);
		this.owner = view.read("Owner", Uuids.CODEC).orElse(null);
		this.authorizedPlayers.clear();
		this.authorizedPlayers.addAll(view.read("AuthorizedPlayers", Uuids.SET_CODEC).orElse(Set.of()));
	}
}
