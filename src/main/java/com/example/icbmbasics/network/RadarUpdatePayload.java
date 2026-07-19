package com.example.icbmbasics.network;

import java.util.List;

import com.example.icbmbasics.ICBMBasics;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.math.BlockPos;

/**
 * S2C: pushed periodically by a radar block entity to every player who has
 * its GUI open, refreshing the scope's contacts and impact log.
 */
public record RadarUpdatePayload(BlockPos pos, List<RadarContact> contacts, List<RadarLogEntry> log)
		implements CustomPayload {
	public static final CustomPayload.Id<RadarUpdatePayload> ID =
			new CustomPayload.Id<>(ICBMBasics.id("radar_update"));

	public static final PacketCodec<RegistryByteBuf, RadarUpdatePayload> CODEC = PacketCodec.of(
			(payload, buf) -> {
				buf.writeBlockPos(payload.pos());
				RadarContact.LIST_PACKET_CODEC.encode(buf, payload.contacts());
				RadarLogEntry.LIST_PACKET_CODEC.encode(buf, payload.log());
			},
			buf -> new RadarUpdatePayload(
					buf.readBlockPos(),
					RadarContact.LIST_PACKET_CODEC.decode(buf),
					RadarLogEntry.LIST_PACKET_CODEC.decode(buf)));

	@Override
	public CustomPayload.Id<? extends CustomPayload> getId() {
		return ID;
	}
}
