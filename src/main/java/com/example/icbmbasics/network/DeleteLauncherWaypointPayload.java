package com.example.icbmbasics.network;

import com.example.icbmbasics.ICBMBasics;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.math.BlockPos;

/**
 * Client -> server: delete button next to a saved waypoint in the launcher GUI.
 */
public record DeleteLauncherWaypointPayload(BlockPos pos, String name) implements CustomPayload {
	public static final CustomPayload.Id<DeleteLauncherWaypointPayload> ID =
			new CustomPayload.Id<>(ICBMBasics.id("delete_launcher_waypoint"));

	public static final PacketCodec<RegistryByteBuf, DeleteLauncherWaypointPayload> CODEC = PacketCodec.of(
			(payload, buf) -> {
				buf.writeBlockPos(payload.pos());
				buf.writeString(payload.name());
			},
			buf -> new DeleteLauncherWaypointPayload(buf.readBlockPos(), buf.readString()));

	@Override
	public CustomPayload.Id<? extends CustomPayload> getId() {
		return ID;
	}
}
