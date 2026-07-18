package com.example.icbmbasics.network;

import java.util.ArrayList;
import java.util.List;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;

/**
 * A named target location ("enemy base", etc.) that a player has saved from
 * a missile launcher's GUI. Shared across the whole world via
 * {@link com.example.icbmbasics.storage.WaypointStorage}.
 */
public record Waypoint(String name, int x, int y, int z) {
	public static final Codec<Waypoint> CODEC = RecordCodecBuilder.create(instance -> instance.group(
			Codec.STRING.fieldOf("Name").forGetter(Waypoint::name),
			Codec.INT.fieldOf("X").forGetter(Waypoint::x),
			Codec.INT.fieldOf("Y").forGetter(Waypoint::y),
			Codec.INT.fieldOf("Z").forGetter(Waypoint::z)
	).apply(instance, Waypoint::new));

	public static final PacketCodec<RegistryByteBuf, Waypoint> PACKET_CODEC = PacketCodec.of(
			(waypoint, buf) -> {
				buf.writeString(waypoint.name());
				buf.writeVarInt(waypoint.x());
				buf.writeVarInt(waypoint.y());
				buf.writeVarInt(waypoint.z());
			},
			buf -> new Waypoint(buf.readString(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt()));

	/** Codec for a whole waypoint list, shared by {@code LauncherScreenData} and {@code WaypointListPayload}. */
	public static final PacketCodec<RegistryByteBuf, List<Waypoint>> LIST_PACKET_CODEC =
			PacketCodecs.collection(ArrayList::new, PACKET_CODEC);
}
