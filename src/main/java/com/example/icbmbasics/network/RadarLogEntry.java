package com.example.icbmbasics.network;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;

/** One line in a radar's impact log: where a tracked missile was last seen before it dropped off. */
public record RadarLogEntry(int x, int y, int z) {
	public static final PacketCodec<RegistryByteBuf, RadarLogEntry> PACKET_CODEC = PacketCodec.of(
			(entry, buf) -> {
				buf.writeVarInt(entry.x());
				buf.writeVarInt(entry.y());
				buf.writeVarInt(entry.z());
			},
			buf -> new RadarLogEntry(buf.readVarInt(), buf.readVarInt(), buf.readVarInt()));

	public static final PacketCodec<RegistryByteBuf, List<RadarLogEntry>> LIST_PACKET_CODEC =
			PacketCodecs.collection(ArrayList::new, PACKET_CODEC);
}
