package com.example.icbmbasics.network;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.util.math.BlockPos;

/**
 * Data sent server -> client when an armored door's keypad GUI opens: its
 * position, whether a code has already been set (picks set-mode vs
 * enter-mode on the client), whether the viewing player is the owner (shows
 * the reset button), and whether they're already an authorized/remembered
 * player (they only reach the GUI at all by sneak-clicking in that case - see
 * {@code ArmoredDoorBlock#onUse}). No code value or other player's identity
 * is ever sent to the client.
 */
public record ArmoredDoorScreenData(BlockPos pos, boolean codeSet, boolean isOwner, boolean isAuthorized) {
	public static final PacketCodec<RegistryByteBuf, ArmoredDoorScreenData> PACKET_CODEC = PacketCodec.of(
			(data, buf) -> {
				buf.writeBlockPos(data.pos());
				buf.writeBoolean(data.codeSet());
				buf.writeBoolean(data.isOwner());
				buf.writeBoolean(data.isAuthorized());
			},
			buf -> new ArmoredDoorScreenData(buf.readBlockPos(), buf.readBoolean(), buf.readBoolean(), buf.readBoolean()));
}
