package com.example.icbmbasics.network;

import java.util.List;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.util.math.BlockPos;

/**
 * Data sent server -> client when the radar GUI opens: its position, tier and
 * detection radius (for scaling the scope), plus whatever it's already
 * tracking. Live updates after that ride on {@link RadarUpdatePayload}.
 */
public record RadarScreenData(BlockPos pos, int tier, int detectionRadius,
		List<RadarContact> contacts, List<RadarLogEntry> log) {
	public static final PacketCodec<RegistryByteBuf, RadarScreenData> PACKET_CODEC = PacketCodec.of(
			(data, buf) -> {
				buf.writeBlockPos(data.pos());
				buf.writeVarInt(data.tier());
				buf.writeVarInt(data.detectionRadius());
				RadarContact.LIST_PACKET_CODEC.encode(buf, data.contacts());
				RadarLogEntry.LIST_PACKET_CODEC.encode(buf, data.log());
			},
			buf -> new RadarScreenData(
					buf.readBlockPos(),
					buf.readVarInt(),
					buf.readVarInt(),
					RadarContact.LIST_PACKET_CODEC.decode(buf),
					RadarLogEntry.LIST_PACKET_CODEC.decode(buf)));
}
