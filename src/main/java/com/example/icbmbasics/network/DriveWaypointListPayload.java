package com.example.icbmbasics.network;

import java.util.List;

import com.example.icbmbasics.ICBMBasics;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;

/**
 * Server -> client: the current full list of waypoints on the drive the
 * player is holding, sent after a save/delete so the open GUI can refresh.
 */
public record DriveWaypointListPayload(List<Waypoint> waypoints) implements CustomPayload {
	public static final CustomPayload.Id<DriveWaypointListPayload> ID =
			new CustomPayload.Id<>(ICBMBasics.id("drive_waypoint_list"));

	public static final PacketCodec<RegistryByteBuf, DriveWaypointListPayload> CODEC = PacketCodec.of(
			(payload, buf) -> Waypoint.LIST_PACKET_CODEC.encode(buf, payload.waypoints()),
			buf -> new DriveWaypointListPayload(Waypoint.LIST_PACKET_CODEC.decode(buf)));

	@Override
	public CustomPayload.Id<? extends CustomPayload> getId() {
		return ID;
	}
}
