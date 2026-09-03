package com.example.icbmbasics.network;

import com.example.icbmbasics.ICBMBasics;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.math.BlockPos;

/**
 * Client -> server: the keypad's Reset button on an armored door. The server
 * re-validates distance and re-checks that the caller is the door's actual
 * owner itself - the client only renders this button because the door's own
 * {@code ArmoredDoorScreenData} told it the viewer is the owner, but that
 * claim is never trusted for the reset itself.
 */
public record ResetDoorCodePayload(BlockPos pos) implements CustomPayload {
	public static final CustomPayload.Id<ResetDoorCodePayload> ID =
			new CustomPayload.Id<>(ICBMBasics.id("reset_door_code"));

	public static final PacketCodec<RegistryByteBuf, ResetDoorCodePayload> CODEC = PacketCodec.of(
			(payload, buf) -> buf.writeBlockPos(payload.pos()),
			buf -> new ResetDoorCodePayload(buf.readBlockPos()));

	@Override
	public CustomPayload.Id<? extends CustomPayload> getId() {
		return ID;
	}
}
