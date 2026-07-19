package com.example.icbmbasics.network;

import com.example.icbmbasics.ICBMBasics;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.math.BlockPos;

/**
 * Client -> server: the keypad's Submit button on an armored door. The
 * server re-validates distance and re-derives set-mode vs enter-mode itself
 * from the block entity - the client's guess at which mode it's in is never
 * trusted for anything but display.
 */
public record SubmitDoorCodePayload(BlockPos pos, int code) implements CustomPayload {
	public static final CustomPayload.Id<SubmitDoorCodePayload> ID =
			new CustomPayload.Id<>(ICBMBasics.id("submit_door_code"));

	public static final PacketCodec<RegistryByteBuf, SubmitDoorCodePayload> CODEC = PacketCodec.of(
			(payload, buf) -> {
				buf.writeBlockPos(payload.pos());
				buf.writeVarInt(payload.code());
			},
			buf -> new SubmitDoorCodePayload(buf.readBlockPos(), buf.readVarInt()));

	@Override
	public CustomPayload.Id<? extends CustomPayload> getId() {
		return ID;
	}
}
