package com.example.icbmbasics.network;

import java.util.List;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.util.math.BlockPos;

/**
 * Data sent server -> client when the launcher GUI opens, so the screen can
 * pre-fill the coordinate fields with the currently stored target and the
 * launcher's own waypoint list. The slotted USB drive's list (if any) is not
 * carried here: it rides along on the USB slot's synced ItemStack instead.
 */
public record LauncherScreenData(BlockPos pos, int targetX, int targetY, int targetZ, boolean hasTarget,
		List<Waypoint> launcherWaypoints) {
	public static final PacketCodec<RegistryByteBuf, LauncherScreenData> PACKET_CODEC = PacketCodec.of(
			(data, buf) -> {
				buf.writeBlockPos(data.pos());
				buf.writeVarInt(data.targetX());
				buf.writeVarInt(data.targetY());
				buf.writeVarInt(data.targetZ());
				buf.writeBoolean(data.hasTarget());
				Waypoint.LIST_PACKET_CODEC.encode(buf, data.launcherWaypoints());
			},
			buf -> new LauncherScreenData(
					buf.readBlockPos(),
					buf.readVarInt(),
					buf.readVarInt(),
					buf.readVarInt(),
					buf.readBoolean(),
					Waypoint.LIST_PACKET_CODEC.decode(buf)));
}
