package com.example.icbmbasics.network;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.util.math.BlockPos;

/**
 * Data sent server -> client when an armored door's keypad GUI opens: its
 * position, and whether a code has already been set (picks set-mode vs
 * enter-mode on the client). No code value is ever sent to the client.
 */
public record ArmoredDoorScreenData(BlockPos pos, boolean codeSet) {
	public static final PacketCodec<RegistryByteBuf, ArmoredDoorScreenData> PACKET_CODEC = PacketCodec.of(
			(data, buf) -> {
				buf.writeBlockPos(data.pos());
				buf.writeBoolean(data.codeSet());
			},
			buf -> new ArmoredDoorScreenData(buf.readBlockPos(), buf.readBoolean()));
}
