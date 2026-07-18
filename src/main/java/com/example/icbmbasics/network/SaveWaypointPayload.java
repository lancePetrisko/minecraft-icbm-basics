package com.example.icbmbasics.network;

import com.example.icbmbasics.ICBMBasics;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;

/**
 * Client -> server: "Save" button in the launcher GUI. Saves (or overwrites)
 * a named waypoint with the coordinates currently typed into the fields.
 */
public record SaveWaypointPayload(String name, int x, int y, int z) implements CustomPayload {
	public static final CustomPayload.Id<SaveWaypointPayload> ID =
			new CustomPayload.Id<>(ICBMBasics.id("save_waypoint"));

	public static final PacketCodec<RegistryByteBuf, SaveWaypointPayload> CODEC = PacketCodec.of(
			(payload, buf) -> {
				buf.writeString(payload.name());
				buf.writeVarInt(payload.x());
				buf.writeVarInt(payload.y());
				buf.writeVarInt(payload.z());
			},
			buf -> new SaveWaypointPayload(buf.readString(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt()));

	@Override
	public CustomPayload.Id<? extends CustomPayload> getId() {
		return ID;
	}
}
