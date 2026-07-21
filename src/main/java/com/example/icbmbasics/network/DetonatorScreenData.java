package com.example.icbmbasics.network;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.util.Hand;

/**
 * Data sent server -> client when a remote detonator's own GUI opens: which
 * hand it's in, and whether that stack is currently linked to a charge block
 * (display only - the server independently re-checks the link when the
 * button is actually pressed, never trusting this snapshot).
 */
public record DetonatorScreenData(Hand hand, boolean linked) {
	public static final PacketCodec<RegistryByteBuf, DetonatorScreenData> PACKET_CODEC = PacketCodec.of(
			(data, buf) -> {
				buf.writeVarInt(data.hand().ordinal());
				buf.writeBoolean(data.linked());
			},
			buf -> new DetonatorScreenData(Hand.values()[buf.readVarInt()], buf.readBoolean()));
}
