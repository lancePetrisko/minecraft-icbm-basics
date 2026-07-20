package com.example.icbmbasics.client.render;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.example.icbmbasics.network.RadarContact;

import net.minecraft.util.math.BlockPos;

import org.jetbrains.annotations.Nullable;

/**
 * Client-side cache of the latest {@code MonitorUpdatePayload} per monitor
 * block, keyed by the monitor's own position. Not tied to any open screen -
 * monitors are always-on world-rendered displays, refreshed by
 * {@code ICBMBasicsClient}'s global payload receiver and read back by
 * {@link MonitorBlockEntityRenderer} every frame.
 */
public final class MonitorRenderData {
	/** A monitor goes blank if it hasn't heard from the server in this long - unlinked, broken, or out of range. */
	private static final long STALE_MILLIS = 3000;

	private static final Map<BlockPos, Snapshot> SNAPSHOTS = new ConcurrentHashMap<>();

	private MonitorRenderData() {
	}

	public record Snapshot(BlockPos radarPos, int detectionRadius, List<RadarContact> contacts, long receivedAtMillis) {
	}

	public static void put(BlockPos monitorPos, BlockPos radarPos, int detectionRadius, List<RadarContact> contacts) {
		SNAPSHOTS.put(monitorPos.toImmutable(),
				new Snapshot(radarPos, detectionRadius, contacts, System.currentTimeMillis()));
	}

	@Nullable
	public static Snapshot get(BlockPos monitorPos) {
		Snapshot snapshot = SNAPSHOTS.get(monitorPos);
		if (snapshot == null || System.currentTimeMillis() - snapshot.receivedAtMillis() > STALE_MILLIS) {
			return null;
		}
		return snapshot;
	}
}
