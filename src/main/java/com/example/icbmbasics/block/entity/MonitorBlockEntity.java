package com.example.icbmbasics.block.entity;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

import com.example.icbmbasics.block.MonitorBlock;
import com.example.icbmbasics.block.WireNetwork;
import com.example.icbmbasics.network.MonitorUpdatePayload;
import com.example.icbmbasics.registry.ModBlockEntities;

import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;

/**
 * Purely a display - no inventory, no GUI. Every {@link #SCAN_INTERVAL_TICKS}
 * it re-validates its link to a radar via {@link #findClusterRadar}, and if
 * linked, pulses that radar to keep it scanning
 * ({@code RadarBlockEntity#pulseFromMonitor}) and pushes its current contacts
 * to every player who can see this monitor via {@link MonitorUpdatePayload} -
 * not gated behind a GUI, since the whole point is an always-on wall display.
 */
public class MonitorBlockEntity extends BlockEntity {
	private static final int SCAN_INTERVAL_TICKS = 10;
	/** Caps the wall flood-fill, mirroring the client renderer's own wall-grouping cap. */
	private static final int MAX_WALL_NODES = 256;

	public MonitorBlockEntity(BlockPos pos, BlockState state) {
		super(ModBlockEntities.MONITOR, pos, state);
	}

	public static void tick(World world, BlockPos pos, BlockState state, MonitorBlockEntity monitor) {
		if (!(world instanceof ServerWorld serverWorld) || world.getTime() % SCAN_INTERVAL_TICKS != 0) {
			return;
		}

		Direction facing = state.contains(MonitorBlock.FACING) ? state.get(MonitorBlock.FACING) : Direction.NORTH;
		BlockPos radarPos = findClusterRadar(world, pos, facing).orElse(null);
		if (radarPos == null || !(world.getBlockEntity(radarPos) instanceof RadarBlockEntity radar)) {
			return;
		}

		radar.pulseFromMonitor(world.getTime());

		MonitorUpdatePayload payload = new MonitorUpdatePayload(
				pos, radarPos, radar.getDetectionRadius(), radar.getContactsSnapshot());
		for (ServerPlayerEntity player : PlayerLookup.tracking(monitor)) {
			ServerPlayNetworking.send(player, payload);
		}
	}

	/**
	 * A wired-in radar anywhere in this monitor's own wall cluster counts for
	 * the whole cluster - flood-fills same-facing {@code MonitorBlock}
	 * neighbors in the wall's own plane (same grouping a player sees rendered
	 * client-side, see {@code MonitorBlockEntityRenderer#computeWallLayout}),
	 * trying {@link WireNetwork#findRadar} from each member and stopping at
	 * the first hit, so only one monitor in a cluster needs its own wire run
	 * to a radar rather than every block needing one.
	 */
	private static Optional<BlockPos> findClusterRadar(World world, BlockPos origin, Direction facing) {
		Direction right = facing.rotateYCounterclockwise();
		Direction[] planeDirs = { right, right.getOpposite(), Direction.UP, Direction.DOWN };

		Set<BlockPos> visited = new HashSet<>();
		Deque<BlockPos> queue = new ArrayDeque<>();
		visited.add(origin);
		queue.add(origin);

		while (!queue.isEmpty() && visited.size() <= MAX_WALL_NODES) {
			BlockPos pos = queue.poll();
			Optional<BlockPos> radar = WireNetwork.findRadar(world, pos);
			if (radar.isPresent()) {
				return radar;
			}

			for (Direction dir : planeDirs) {
				BlockPos neighbor = pos.offset(dir);
				if (!visited.add(neighbor)) {
					continue;
				}
				if (!world.isChunkLoaded(neighbor)) {
					continue;
				}
				BlockState neighborState = world.getBlockState(neighbor);
				if (!(neighborState.getBlock() instanceof MonitorBlock) || neighborState.get(MonitorBlock.FACING) != facing) {
					continue;
				}
				queue.add(neighbor);
			}
		}

		return Optional.empty();
	}
}
