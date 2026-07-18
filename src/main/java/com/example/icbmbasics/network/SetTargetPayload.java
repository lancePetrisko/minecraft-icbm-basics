package com.example.icbmbasics.network;

import com.example.icbmbasics.ICBMBasics;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.math.BlockPos;

/**
 * Client -> server: "Confirm Target" button in the launcher GUI.
 * The server re-validates everything (distance, block entity, Y bounds).
 */
public record SetTargetPayload(BlockPos pos, int x, int y, int z) implements CustomPayload {
	public static final CustomPayload.Id<SetTargetPayload> ID =
			new CustomPayload.Id<>(ICBMBasics.id("set_target"));

	public static final PacketCodec<RegistryByteBuf, SetTargetPayload> CODEC = PacketCodec.of(
			(payload, buf) -> {
				buf.writeBlockPos(payload.pos());
				buf.writeVarInt(payload.x());
				buf.writeVarInt(payload.y());
				buf.writeVarInt(payload.z());
			},
			buf -> new SetTargetPayload(buf.readBlockPos(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt()));

	@Override
	public CustomPayload.Id<? extends CustomPayload> getId() {
		return ID;
	}
}
