package com.example.icbmbasics.block;

import com.example.icbmbasics.registry.ModBlocks;

import net.minecraft.block.Block;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Optional;
import java.util.Queue;
import java.util.Set;

/**
 * A SAM site, CIWS, or monitor only operates while it can reach a
 * {@link RadarBlock} through a chain of {@code WIRE} blocks (or direct
 * adjacency to a radar). Just a breadth-first search over orthogonal
 * neighbors - {@code ModBlocks.WIRE} blocks are traversed through, anything
 * else is a dead end unless it's a radar.
 */
public final class WireNetwork {
	/** Caps how far a wire chain can be searched, so a runaway network can't stall the server. */
	private static final int MAX_NODES = 4096;

	private WireNetwork() {
	}

	/** Finds the position of the nearest reachable radar, if any. */
	public static Optional<BlockPos> findRadar(World world, BlockPos origin) {
		Queue<BlockPos> queue = new ArrayDeque<>();
		Set<BlockPos> visited = new HashSet<>();
		queue.add(origin);
		visited.add(origin);

		while (!queue.isEmpty() && visited.size() <= MAX_NODES) {
			BlockPos pos = queue.poll();
			for (Direction direction : Direction.values()) {
				BlockPos neighbor = pos.offset(direction);
				if (!visited.add(neighbor)) {
					continue;
				}
				Block block = world.getBlockState(neighbor).getBlock();
				if (block instanceof RadarBlock) {
					return Optional.of(neighbor);
				}
				if (block == ModBlocks.WIRE) {
					queue.add(neighbor);
				}
			}
		}
		return Optional.empty();
	}

	public static boolean isConnectedToRadar(World world, BlockPos origin) {
		return findRadar(world, origin).isPresent();
	}
}
