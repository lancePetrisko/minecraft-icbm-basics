package com.example.icbmbasics.network;

import com.example.icbmbasics.ICBMBasics;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.math.BlockPos;

/**
 * Client -> server: "Save" button in the launcher GUI. Saves (or overwrites)
 * a named waypoint on the launcher block entity at {@code pos}.
 */
public record SaveLauncherWaypointPayload(BlockPos pos, String name, int x, int y, int z) implements CustomPayload {
	public static final CustomPayload.Id<SaveLauncherWaypointPayload> ID =
			new CustomPayload.Id<>(ICBMBasics.id("save_launcher_waypoint"));

	public static final PacketCodec<RegistryByteBuf, SaveLauncherWaypointPayload> CODEC = PacketCodec.of(
			(payload, buf) -> {
				buf.writeBlockPos(payload.pos());
				buf.writeString(payload.name());
				buf.writeVarInt(payload.x());
				buf.writeVarInt(payload.y());
				buf.writeVarInt(payload.z());
			},
			buf -> new SaveLauncherWaypointPayload(buf.readBlockPos(), buf.readString(),
					buf.readVarInt(), buf.readVarInt(), buf.readVarInt()));

	@Override
	public CustomPayload.Id<? extends CustomPayload> getId() {
		return ID;
	}
}
