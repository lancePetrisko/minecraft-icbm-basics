package com.example.icbmbasics.network;

import com.example.icbmbasics.ICBMBasics;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;

/**
 * Client -> server: delete button next to a saved waypoint in the launcher GUI.
 */
public record DeleteWaypointPayload(String name) implements CustomPayload {
	public static final CustomPayload.Id<DeleteWaypointPayload> ID =
			new CustomPayload.Id<>(ICBMBasics.id("delete_waypoint"));

	public static final PacketCodec<RegistryByteBuf, DeleteWaypointPayload> CODEC = PacketCodec.of(
			(payload, buf) -> buf.writeString(payload.name()),
			buf -> new DeleteWaypointPayload(buf.readString()));

	@Override
	public CustomPayload.Id<? extends CustomPayload> getId() {
		return ID;
	}
}
