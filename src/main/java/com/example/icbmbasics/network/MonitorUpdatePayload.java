package com.example.icbmbasics.network;

import java.util.List;

import com.example.icbmbasics.ICBMBasics;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.math.BlockPos;

/**
 * S2C: pushed periodically by a monitor block entity to every player who can
 * see it (not gated behind a GUI, unlike {@link RadarUpdatePayload}), carrying
 * the linked radar's own position (needed for the client to compute blip
 * offsets, same as {@code RadarScreen} does) plus its detection radius and
 * current contacts. No log - a wall monitor is a live scope only, not the
 * scrollable impact-log view.
 */
public record MonitorUpdatePayload(BlockPos monitorPos, BlockPos radarPos, int detectionRadius,
		List<RadarContact> contacts) implements CustomPayload {
	public static final CustomPayload.Id<MonitorUpdatePayload> ID =
			new CustomPayload.Id<>(ICBMBasics.id("monitor_update"));

	public static final PacketCodec<RegistryByteBuf, MonitorUpdatePayload> CODEC = PacketCodec.of(
			(payload, buf) -> {
				buf.writeBlockPos(payload.monitorPos());
				buf.writeBlockPos(payload.radarPos());
				buf.writeVarInt(payload.detectionRadius());
				RadarContact.LIST_PACKET_CODEC.encode(buf, payload.contacts());
			},
			buf -> new MonitorUpdatePayload(
					buf.readBlockPos(),
					buf.readBlockPos(),
					buf.readVarInt(),
					RadarContact.LIST_PACKET_CODEC.decode(buf)));

	@Override
	public CustomPayload.Id<? extends CustomPayload> getId() {
		return ID;
	}
}
