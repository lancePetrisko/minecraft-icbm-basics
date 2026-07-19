package com.example.icbmbasics.network;

import java.util.List;

import com.example.icbmbasics.ICBMBasics;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;

/**
 * Server -> client: the launcher's own current waypoint list, sent after a
 * save/delete so the requesting client's GUI can refresh.
 */
public record LauncherWaypointListPayload(List<Waypoint> launcherWaypoints) implements CustomPayload {
	public static final CustomPayload.Id<LauncherWaypointListPayload> ID =
			new CustomPayload.Id<>(ICBMBasics.id("launcher_waypoint_list"));

	public static final PacketCodec<RegistryByteBuf, LauncherWaypointListPayload> CODEC = PacketCodec.of(
			(payload, buf) -> Waypoint.LIST_PACKET_CODEC.encode(buf, payload.launcherWaypoints()),
			buf -> new LauncherWaypointListPayload(Waypoint.LIST_PACKET_CODEC.decode(buf)));

	@Override
	public CustomPayload.Id<? extends CustomPayload> getId() {
		return ID;
	}
}
