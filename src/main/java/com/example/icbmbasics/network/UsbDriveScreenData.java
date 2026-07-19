package com.example.icbmbasics.network;

import java.util.List;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.util.Hand;

/**
 * Data sent server -> client when the USB drive's own GUI opens, so the
 * screen can pre-fill the waypoint list from the held stack.
 */
public record UsbDriveScreenData(Hand hand, List<Waypoint> waypoints) {
	public static final PacketCodec<RegistryByteBuf, UsbDriveScreenData> PACKET_CODEC = PacketCodec.of(
			(data, buf) -> {
				buf.writeVarInt(data.hand().ordinal());
				Waypoint.LIST_PACKET_CODEC.encode(buf, data.waypoints());
			},
			buf -> new UsbDriveScreenData(Hand.values()[buf.readVarInt()], Waypoint.LIST_PACKET_CODEC.decode(buf)));
}
