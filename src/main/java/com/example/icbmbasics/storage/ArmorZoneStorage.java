package com.example.icbmbasics.storage;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.example.icbmbasics.ICBMBasics;
import com.example.icbmbasics.config.ICBMConfig;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.datafixer.DataFixTypes;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.PersistentState;
import net.minecraft.world.PersistentStateType;

/**
 * Per-dimension list of independent "armor zones": an anchor point plus how
 * many armored blocks/doors currently count against it. Placing an armored
 * block/door claims a slot in whichever zone's anchor is within
 * {@code armorZoneRadius}, creating a fresh zone if none is in range. Keeps
 * players from walling off unlimited area in blast-proof blocks.
 */
public class ArmorZoneStorage extends PersistentState {
	private static final Codec<ArmorZone> ZONE_CODEC = RecordCodecBuilder.create(instance -> instance.group(
			BlockPos.CODEC.fieldOf("Anchor").forGetter(ArmorZone::anchor),
			Codec.INT.fieldOf("Count").forGetter(ArmorZone::count)
	).apply(instance, ArmorZone::new));

	private static final Codec<ArmorZoneStorage> CODEC = RecordCodecBuilder.create(instance -> instance.group(
			ZONE_CODEC.listOf().fieldOf("Zones").forGetter(ArmorZoneStorage::getZones)
	).apply(instance, ArmorZoneStorage::new));

	public static final PersistentStateType<ArmorZoneStorage> STATE_TYPE = new PersistentStateType<>(
			"icbmbasics_armor_zones", ArmorZoneStorage::new, CODEC, DataFixTypes.LEVEL);

	/** Mutable holder kept internally; {@link #getZones()} snapshots it for the codec/callers. */
	private static final class ArmorZone {
		BlockPos anchor;
		int count;

		ArmorZone(BlockPos anchor, int count) {
			this.anchor = anchor;
			this.count = count;
		}

		BlockPos anchor() {
			return this.anchor;
		}

		int count() {
			return this.count;
		}
	}

	private final List<ArmorZone> zones = new ArrayList<>();

	public ArmorZoneStorage() {
	}

	private ArmorZoneStorage(List<ArmorZone> zones) {
		this.zones.addAll(zones);
	}

	public static ArmorZoneStorage get(ServerWorld world) {
		return world.getPersistentStateManager().getOrCreate(STATE_TYPE);
	}

	private List<ArmorZone> getZones() {
		return List.copyOf(this.zones);
	}

	private Optional<ArmorZone> nearest(BlockPos pos) {
		return this.zones.stream().min((a, b) ->
				Double.compare(a.anchor.getSquaredDistance(pos), b.anchor.getSquaredDistance(pos)));
	}

	private Optional<ArmorZone> nearestInRange(BlockPos pos, int radius) {
		double radiusSq = (double) radius * radius;
		return this.nearest(pos).filter(zone -> zone.anchor.getSquaredDistance(pos) <= radiusSq);
	}

	/** Read-only: would a block placed at {@code pos} fit under its zone's cap? */
	public boolean canClaim(BlockPos pos, int radius, int maxBlocks) {
		return this.nearestInRange(pos, radius).map(zone -> zone.count < maxBlocks).orElse(true);
	}

	/**
	 * Claims a slot for a newly placed armored block at {@code pos}. Joins the
	 * nearest zone within {@code radius} if one exists, otherwise starts a
	 * brand new zone anchored at {@code pos}. Only call this after
	 * {@link #canClaim} has already confirmed there's room - placement itself
	 * should be refused via {@link #checkPlacement} before the block ever
	 * exists, so this never needs to fail/undo.
	 */
	public void claim(BlockPos pos, int radius) {
		Optional<ArmorZone> existing = this.nearestInRange(pos, radius);
		if (existing.isPresent()) {
			existing.get().count++;
		} else {
			this.zones.add(new ArmorZone(pos.toImmutable(), 1));
		}
		this.markDirty();
	}

	/**
	 * Gate to call from {@code Block#getPlacementState}: returns whether
	 * placement at the context's target position should be allowed, messaging
	 * the placer and refusing if the nearest zone is already at capacity.
	 * Always allows on the client (speculative placement preview) - the
	 * server call is authoritative.
	 */
	public static boolean checkPlacement(ItemPlacementContext ctx) {
		if (!(ctx.getWorld() instanceof ServerWorld serverWorld)) {
			return true;
		}
		ICBMConfig config = ICBMBasics.CONFIG;
		boolean ok = get(serverWorld).canClaim(ctx.getBlockPos(), config.armorZoneRadius, config.armorZoneMaxBlocks);
		if (!ok && ctx.getPlayer() != null) {
			ctx.getPlayer().sendMessage(
					Text.translatable("message.icbmbasics.armor_zone_full", config.armorZoneMaxBlocks), true);
		}
		return ok;
	}

	/** Releases a slot when an armored block/door at {@code pos} is broken. Removes the zone if it hits zero. */
	public void release(BlockPos pos, int radius) {
		this.nearestInRange(pos, radius).ifPresent(zone -> {
			zone.count = Math.max(0, zone.count - 1);
			if (zone.count == 0) {
				this.zones.remove(zone);
			}
			this.markDirty();
		});
	}

	/** Moves the globally nearest zone's anchor to {@code pos}. Returns its (unchanged) count, if any zone exists. */
	public Optional<Integer> reanchor(BlockPos pos) {
		Optional<ArmorZone> nearest = this.nearest(pos);
		nearest.ifPresent(zone -> {
			zone.anchor = pos.toImmutable();
			this.markDirty();
		});
		return nearest.map(zone -> zone.count);
	}
}
