package com.example.icbmbasics.network;

import com.example.icbmbasics.ICBMBasics;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Hand;

/**
 * Client -> server: the detonator GUI's red button, pressed after the launch
 * protection cover is flipped up. Keyed by {@link Hand}, same reasoning as
 * the USB drive's own payloads - the server resolves the actual stack (and
 * its {@code ModComponents#DETONATOR_LINK}) itself rather than trusting a
 * client-supplied position.
 */
public record TriggerDetonatorPayload(Hand hand) implements CustomPayload {
	public static final CustomPayload.Id<TriggerDetonatorPayload> ID =
			new CustomPayload.Id<>(ICBMBasics.id("trigger_detonator"));

	public static final PacketCodec<RegistryByteBuf, TriggerDetonatorPayload> CODEC = PacketCodec.of(
			(payload, buf) -> buf.writeVarInt(payload.hand().ordinal()),
			buf -> new TriggerDetonatorPayload(Hand.values()[buf.readVarInt()]));

	@Override
	public CustomPayload.Id<? extends CustomPayload> getId() {
		return ID;
	}
}
