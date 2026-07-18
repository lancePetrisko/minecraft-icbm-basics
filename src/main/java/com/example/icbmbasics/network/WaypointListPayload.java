package com.example.icbmbasics.network;

import java.util.List;

import com.example.icbmbasics.ICBMBasics;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;

/**
 * Server -> client: the current full list of saved waypoints, sent after a
 * save/delete so the requesting client's GUI can refresh.
 */
public record WaypointListPayload(List<Waypoint> waypoints) implements CustomPayload {
	public static final CustomPayload.Id<WaypointListPayload> ID =
			new CustomPayload.Id<>(ICBMBasics.id("waypoint_list"));

	public static final PacketCodec<RegistryByteBuf, WaypointListPayload> CODEC = PacketCodec.of(
			(payload, buf) -> Waypoint.LIST_PACKET_CODEC.encode(buf, payload.waypoints()),
			buf -> new WaypointListPayload(Waypoint.LIST_PACKET_CODEC.decode(buf)));

	@Override
	public CustomPayload.Id<? extends CustomPayload> getId() {
		return ID;
	}
}
