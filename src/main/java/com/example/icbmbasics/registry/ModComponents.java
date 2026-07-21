package com.example.icbmbasics.registry;

import java.util.List;

import com.example.icbmbasics.ICBMBasics;
import com.example.icbmbasics.network.Waypoint;

import net.minecraft.component.ComponentType;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.math.GlobalPos;

public final class ModComponents {
	/** Named waypoints stored directly on a USB drive item stack. */
	public static final ComponentType<List<Waypoint>> WAYPOINTS = Registry.register(Registries.DATA_COMPONENT_TYPE,
			ICBMBasics.id("waypoints"),
			ComponentType.<List<Waypoint>>builder()
					.codec(Waypoint.CODEC.listOf())
					.packetCodec(Waypoint.LIST_PACKET_CODEC)
					.build());

	/**
	 * Which {@code DetonatorChargeBlock} a remote detonator is bound to - set
	 * by right-clicking the charge block, reused by vanilla's own
	 * {@code GlobalPos} (dimension + BlockPos) rather than a bespoke record,
	 * same shape as a lodestone compass's tracker.
	 */
	public static final ComponentType<GlobalPos> DETONATOR_LINK = Registry.register(Registries.DATA_COMPONENT_TYPE,
			ICBMBasics.id("detonator_link"),
			ComponentType.<GlobalPos>builder()
					.codec(GlobalPos.CODEC)
					.packetCodec(GlobalPos.PACKET_CODEC)
					.build());

	private ModComponents() {
	}

	public static void register() {
	}
}
