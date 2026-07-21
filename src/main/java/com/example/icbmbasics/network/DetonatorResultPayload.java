package com.example.icbmbasics.network;

import com.example.icbmbasics.ICBMBasics;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;

/**
 * Server -> client: the outcome of a {@link TriggerDetonatorPayload} press,
 * so the GUI can show "DETONATED!" or a failure reason. The trigger is only
 * ever resolved server-side (link validity, whether the charge block is
 * still there), so the client can't know the real outcome until this arrives.
 */
public record DetonatorResultPayload(boolean success) implements CustomPayload {
	public static final CustomPayload.Id<DetonatorResultPayload> ID =
			new CustomPayload.Id<>(ICBMBasics.id("detonator_result"));

	public static final PacketCodec<RegistryByteBuf, DetonatorResultPayload> CODEC = PacketCodec.of(
			(payload, buf) -> buf.writeBoolean(payload.success()),
			buf -> new DetonatorResultPayload(buf.readBoolean()));

	@Override
	public CustomPayload.Id<? extends CustomPayload> getId() {
		return ID;
	}
}
