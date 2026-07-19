package com.example.icbmbasics.network;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;

/**
 * A single missile blip on a radar scope: current position plus whether this
 * radar acquired it at launch (tracked map-wide until impact) or is only
 * seeing it because it's currently within detection range.
 */
public record RadarContact(UUID id, double x, double y, double z, boolean outgoing) {
	public static final PacketCodec<RegistryByteBuf, RadarContact> PACKET_CODEC = PacketCodec.of(
			(contact, buf) -> {
				buf.writeUuid(contact.id());
				buf.writeDouble(contact.x());
				buf.writeDouble(contact.y());
				buf.writeDouble(contact.z());
				buf.writeBoolean(contact.outgoing());
			},
			buf -> new RadarContact(buf.readUuid(), buf.readDouble(), buf.readDouble(), buf.readDouble(),
					buf.readBoolean()));

	public static final PacketCodec<RegistryByteBuf, List<RadarContact>> LIST_PACKET_CODEC =
			PacketCodecs.collection(ArrayList::new, PACKET_CODEC);
}
