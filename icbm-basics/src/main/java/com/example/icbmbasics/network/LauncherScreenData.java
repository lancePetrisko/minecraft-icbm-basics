package com.example.icbmbasics.network;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.util.math.BlockPos;

/**
 * Data sent server -> client when the launcher GUI opens, so the screen can
 * pre-fill the coordinate fields with the currently stored target.
 */
public record LauncherScreenData(BlockPos pos, int targetX, int targetY, int targetZ, boolean hasTarget) {
	public static final PacketCodec<RegistryByteBuf, LauncherScreenData> PACKET_CODEC = PacketCodec.of(
			(data, buf) -> {
				buf.writeBlockPos(data.pos());
				buf.writeVarInt(data.targetX());
				buf.writeVarInt(data.targetY());
				buf.writeVarInt(data.targetZ());
				buf.writeBoolean(data.hasTarget());
			},
			buf -> new LauncherScreenData(
					buf.readBlockPos(),
					buf.readVarInt(),
					buf.readVarInt(),
					buf.readVarInt(),
					buf.readBoolean()));
}
