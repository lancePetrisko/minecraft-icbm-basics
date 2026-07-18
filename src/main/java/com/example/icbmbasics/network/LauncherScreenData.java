package com.example.icbmbasics.network;

import java.util.List;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.util.math.BlockPos;

/**
 * Data sent server -> client when the launcher GUI opens, so the screen can
 * pre-fill the coordinate fields with the currently stored target and the
 * list of saved waypoints.
 */
public record LauncherScreenData(BlockPos pos, int targetX, int targetY, int targetZ, boolean hasTarget,
		List<Waypoint> waypoints) {
	public static final PacketCodec<RegistryByteBuf, LauncherScreenData> PACKET_CODEC = PacketCodec.of(
			(data, buf) -> {
				buf.writeBlockPos(data.pos());
				buf.writeVarInt(data.targetX());
				buf.writeVarInt(data.targetY());
				buf.writeVarInt(data.targetZ());
				buf.writeBoolean(data.hasTarget());
				Waypoint.LIST_PACKET_CODEC.encode(buf, data.waypoints());
			},
			buf -> new LauncherScreenData(
					buf.readBlockPos(),
					buf.readVarInt(),
					buf.readVarInt(),
					buf.readVarInt(),
					buf.readBoolean(),
					Waypoint.LIST_PACKET_CODEC.decode(buf)));
}
