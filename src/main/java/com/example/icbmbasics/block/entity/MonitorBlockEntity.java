package com.example.icbmbasics.block.entity;

import com.example.icbmbasics.block.WireNetwork;
import com.example.icbmbasics.network.MonitorUpdatePayload;
import com.example.icbmbasics.registry.ModBlockEntities;

import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/**
 * Purely a display - no inventory, no GUI. Every {@link #SCAN_INTERVAL_TICKS}
 * it re-validates its link to a radar via {@link WireNetwork#findRadar}
 * (exactly like {@code SamSiteBlockEntity}/{@code CiwsBlockEntity} do for
 * firing), and if linked, pulses that radar to keep it scanning
 * ({@code RadarBlockEntity#pulseFromMonitor}) and pushes its current contacts
 * to every player who can see this monitor via {@link MonitorUpdatePayload} -
 * not gated behind a GUI, since the whole point is an always-on wall display.
 */
public class MonitorBlockEntity extends BlockEntity {
	private static final int SCAN_INTERVAL_TICKS = 10;

	public MonitorBlockEntity(BlockPos pos, BlockState state) {
		super(ModBlockEntities.MONITOR, pos, state);
	}

	public static void tick(World world, BlockPos pos, BlockState state, MonitorBlockEntity monitor) {
		if (!(world instanceof ServerWorld serverWorld) || world.getTime() % SCAN_INTERVAL_TICKS != 0) {
			return;
		}

		BlockPos radarPos = WireNetwork.findRadar(world, pos).orElse(null);
		if (radarPos == null || !(world.getBlockEntity(radarPos) instanceof RadarBlockEntity radar)) {
			return;
		}

		radar.pulseFromMonitor(world.getTime());

		MonitorUpdatePayload payload = new MonitorUpdatePayload(
				pos, radarPos, radar.getDetectionRadius(), radar.getContactsSnapshot());
		for (ServerPlayerEntity player : PlayerLookup.tracking(monitor)) {
			ServerPlayNetworking.send(player, payload);
		}
	}
}
