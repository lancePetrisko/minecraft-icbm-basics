package com.example.icbmbasics.network;

import com.example.icbmbasics.ICBMBasics;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Hand;

/**
 * Client -> server: delete button next to a saved waypoint in the USB drive GUI.
 */
public record DeleteDriveWaypointPayload(Hand hand, String name) implements CustomPayload {
	public static final CustomPayload.Id<DeleteDriveWaypointPayload> ID =
			new CustomPayload.Id<>(ICBMBasics.id("delete_drive_waypoint"));

	public static final PacketCodec<RegistryByteBuf, DeleteDriveWaypointPayload> CODEC = PacketCodec.of(
			(payload, buf) -> {
				buf.writeVarInt(payload.hand().ordinal());
				buf.writeString(payload.name());
			},
			buf -> new DeleteDriveWaypointPayload(Hand.values()[buf.readVarInt()], buf.readString()));

	@Override
	public CustomPayload.Id<? extends CustomPayload> getId() {
		return ID;
	}
}
