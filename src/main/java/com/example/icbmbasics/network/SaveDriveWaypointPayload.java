package com.example.icbmbasics.network;

import com.example.icbmbasics.ICBMBasics;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Hand;

/**
 * Client -> server: "Save" button in the USB drive GUI. Saves (or overwrites)
 * a named waypoint on whichever hand is holding the drive.
 */
public record SaveDriveWaypointPayload(Hand hand, String name, int x, int y, int z) implements CustomPayload {
	public static final CustomPayload.Id<SaveDriveWaypointPayload> ID =
			new CustomPayload.Id<>(ICBMBasics.id("save_drive_waypoint"));

	public static final PacketCodec<RegistryByteBuf, SaveDriveWaypointPayload> CODEC = PacketCodec.of(
			(payload, buf) -> {
				buf.writeVarInt(payload.hand().ordinal());
				buf.writeString(payload.name());
				buf.writeVarInt(payload.x());
				buf.writeVarInt(payload.y());
				buf.writeVarInt(payload.z());
			},
			buf -> new SaveDriveWaypointPayload(Hand.values()[buf.readVarInt()], buf.readString(),
					buf.readVarInt(), buf.readVarInt(), buf.readVarInt()));

	@Override
	public CustomPayload.Id<? extends CustomPayload> getId() {
		return ID;
	}
}
