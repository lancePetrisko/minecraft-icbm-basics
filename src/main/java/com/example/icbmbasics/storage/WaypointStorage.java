package com.example.icbmbasics.storage;

import java.util.ArrayList;
import java.util.List;

import com.example.icbmbasics.network.Waypoint;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.datafixer.DataFixTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.PersistentState;
import net.minecraft.world.PersistentStateType;

/**
 * World-wide list of named target coordinates, saved from any missile
 * launcher's GUI and available to all of them. Always stored on the
 * overworld so it stays a single shared list regardless of which
 * dimension a given launcher sits in.
 */
public class WaypointStorage extends PersistentState {
	private static final Codec<WaypointStorage> CODEC = RecordCodecBuilder.create(instance -> instance.group(
			Waypoint.CODEC.listOf().fieldOf("Waypoints").forGetter(WaypointStorage::getAll)
	).apply(instance, WaypointStorage::new));

	public static final PersistentStateType<WaypointStorage> STATE_TYPE = new PersistentStateType<>(
			"icbmbasics_waypoints", WaypointStorage::new, CODEC, DataFixTypes.LEVEL);

	private final List<Waypoint> waypoints = new ArrayList<>();

	public WaypointStorage() {
	}

	private WaypointStorage(List<Waypoint> waypoints) {
		this.waypoints.addAll(waypoints);
	}

	public List<Waypoint> getAll() {
		return List.copyOf(this.waypoints);
	}

	/** Saves the waypoint, overwriting any existing one with the same name (case-insensitive). */
	public void save(Waypoint waypoint) {
		this.waypoints.removeIf(w -> w.name().equalsIgnoreCase(waypoint.name()));
		this.waypoints.add(waypoint);
		this.markDirty();
	}

	public void remove(String name) {
		if (this.waypoints.removeIf(w -> w.name().equalsIgnoreCase(name))) {
			this.markDirty();
		}
	}

	public static WaypointStorage get(ServerWorld world) {
		ServerWorld overworld = world.getServer().getOverworld();
		return overworld.getPersistentStateManager().getOrCreate(STATE_TYPE);
	}
}
