package com.example.icbmbasics.registry;

import java.util.List;

import com.example.icbmbasics.ICBMBasics;
import com.example.icbmbasics.network.Waypoint;

import net.minecraft.component.ComponentType;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;

public final class ModComponents {
	/** Named waypoints stored directly on a USB drive item stack. */
	public static final ComponentType<List<Waypoint>> WAYPOINTS = Registry.register(Registries.DATA_COMPONENT_TYPE,
			ICBMBasics.id("waypoints"),
			ComponentType.<List<Waypoint>>builder()
					.codec(Waypoint.CODEC.listOf())
					.packetCodec(Waypoint.LIST_PACKET_CODEC)
					.build());

	private ModComponents() {
	}

	public static void register() {
	}
}
