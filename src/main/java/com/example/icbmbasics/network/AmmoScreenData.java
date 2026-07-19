package com.example.icbmbasics.network;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.util.math.BlockPos;

/**
 * Data sent server -> client when a SAM site or CIWS ammo GUI opens: just its
 * position. The ammo slot's contents themselves ride on the normal
 * screen-handler slot sync, same as the missile launcher's ammo slot - no
 * count/capacity needs sending separately.
 */
public record AmmoScreenData(BlockPos pos) {
	public static final PacketCodec<RegistryByteBuf, AmmoScreenData> PACKET_CODEC = PacketCodec.of(
			(data, buf) -> buf.writeBlockPos(data.pos()),
			buf -> new AmmoScreenData(buf.readBlockPos()));
}
