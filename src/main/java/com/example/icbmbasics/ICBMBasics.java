package com.example.icbmbasics;

import java.util.ArrayList;
import java.util.List;

import com.example.icbmbasics.block.DetonatorChargeBlock;
import com.example.icbmbasics.block.entity.ArmoredDoorBlockEntity;
import com.example.icbmbasics.block.entity.MissileLauncherBlockEntity;
import com.example.icbmbasics.config.ICBMConfig;
import com.example.icbmbasics.network.DeleteDriveWaypointPayload;
import com.example.icbmbasics.network.DeleteLauncherWaypointPayload;
import com.example.icbmbasics.network.DetonatorResultPayload;
import com.example.icbmbasics.network.DriveWaypointListPayload;
import com.example.icbmbasics.network.LauncherWaypointListPayload;
import com.example.icbmbasics.network.MonitorUpdatePayload;
import com.example.icbmbasics.network.RadarUpdatePayload;
import com.example.icbmbasics.network.SaveDriveWaypointPayload;
import com.example.icbmbasics.network.SaveLauncherWaypointPayload;
import com.example.icbmbasics.network.SetTargetPayload;
import com.example.icbmbasics.network.SubmitDoorCodePayload;
import com.example.icbmbasics.network.TriggerDetonatorPayload;
import com.example.icbmbasics.network.Waypoint;
import com.example.icbmbasics.registry.ModBlockEntities;
import com.example.icbmbasics.registry.ModBlocks;
import com.example.icbmbasics.registry.ModComponents;
import com.example.icbmbasics.registry.ModEntities;
import com.example.icbmbasics.registry.ModItemGroups;
import com.example.icbmbasics.registry.ModItems;
import com.example.icbmbasics.registry.ModScreenHandlers;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.block.BlockState;
import net.minecraft.block.DoorBlock;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.GlobalPos;
import net.minecraft.util.math.MathHelper;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ICBMBasics implements ModInitializer {
	public static final String MOD_ID = "icbmbasics";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	/** Loaded from config/icbmbasics.json on startup. */
	public static ICBMConfig CONFIG = new ICBMConfig();

	public static Identifier id(String path) {
		return Identifier.of(MOD_ID, path);
	}

	@Override
	public void onInitialize() {
		CONFIG = ICBMConfig.load();

		ModItemGroups.register();
		ModItems.register();
		ModBlocks.register();
		ModBlockEntities.register();
		ModEntities.register();
		ModScreenHandlers.register();
		ModComponents.register();

		// C2S payload: the launcher GUI's "Confirm Target" button.
		PayloadTypeRegistry.playC2S().register(SetTargetPayload.ID, SetTargetPayload.CODEC);

		ServerPlayNetworking.registerGlobalReceiver(SetTargetPayload.ID, (payload, context) -> {
			ServerPlayerEntity player = context.player();
			if (!(player.getEntityWorld() instanceof ServerWorld world)) {
				return;
			}
			// Server-authoritative validation: the player must actually be near the launcher.
			if (!player.getBlockPos().isWithinDistance(payload.pos(), 8.0)) {
				return;
			}
			BlockEntity be = world.getBlockEntity(payload.pos());
			if (be instanceof MissileLauncherBlockEntity launcher) {
				int y = MathHelper.clamp(payload.y(), world.getBottomY(), world.getTopYInclusive());
				launcher.setTarget(payload.x(), y, payload.z());
			}
		});

		// C2S payloads: saving/deleting named waypoints on a launcher's own list.
		PayloadTypeRegistry.playC2S().register(SaveLauncherWaypointPayload.ID, SaveLauncherWaypointPayload.CODEC);
		PayloadTypeRegistry.playC2S().register(DeleteLauncherWaypointPayload.ID, DeleteLauncherWaypointPayload.CODEC);
		// S2C payload: the refreshed launcher waypoint list, sent back after a save/delete.
		PayloadTypeRegistry.playS2C().register(LauncherWaypointListPayload.ID, LauncherWaypointListPayload.CODEC);

		ServerPlayNetworking.registerGlobalReceiver(SaveLauncherWaypointPayload.ID, (payload, context) -> {
			ServerPlayerEntity player = context.player();
			if (!(player.getEntityWorld() instanceof ServerWorld world)) {
				return;
			}
			if (!player.getBlockPos().isWithinDistance(payload.pos(), 8.0)) {
				return;
			}
			String name = payload.name().trim();
			if (name.isEmpty() || name.length() > 32) {
				return;
			}
			if (!(world.getBlockEntity(payload.pos()) instanceof MissileLauncherBlockEntity launcher)) {
				return;
			}
			int y = MathHelper.clamp(payload.y(), world.getBottomY(), world.getTopYInclusive());
			launcher.saveWaypoint(new Waypoint(name, payload.x(), y, payload.z()));
			ServerPlayNetworking.send(player, new LauncherWaypointListPayload(launcher.getWaypoints()));
		});

		ServerPlayNetworking.registerGlobalReceiver(DeleteLauncherWaypointPayload.ID, (payload, context) -> {
			ServerPlayerEntity player = context.player();
			if (!(player.getEntityWorld() instanceof ServerWorld world)) {
				return;
			}
			if (!player.getBlockPos().isWithinDistance(payload.pos(), 8.0)) {
				return;
			}
			if (!(world.getBlockEntity(payload.pos()) instanceof MissileLauncherBlockEntity launcher)) {
				return;
			}
			launcher.removeWaypoint(payload.name());
			ServerPlayNetworking.send(player, new LauncherWaypointListPayload(launcher.getWaypoints()));
		});

		// C2S payloads: saving/deleting named waypoints stored directly on a held USB drive.
		PayloadTypeRegistry.playC2S().register(SaveDriveWaypointPayload.ID, SaveDriveWaypointPayload.CODEC);
		PayloadTypeRegistry.playC2S().register(DeleteDriveWaypointPayload.ID, DeleteDriveWaypointPayload.CODEC);
		// S2C payload: the refreshed drive waypoint list, sent back after a save/delete.
		PayloadTypeRegistry.playS2C().register(DriveWaypointListPayload.ID, DriveWaypointListPayload.CODEC);

		ServerPlayNetworking.registerGlobalReceiver(SaveDriveWaypointPayload.ID, (payload, context) -> {
			ServerPlayerEntity player = context.player();
			ItemStack stack = player.getStackInHand(payload.hand());
			if (!stack.isOf(ModItems.USB_DRIVE)) {
				return;
			}
			String name = payload.name().trim();
			if (name.isEmpty() || name.length() > 32) {
				return;
			}
			ServerWorld world = (ServerWorld) player.getEntityWorld();
			int y = MathHelper.clamp(payload.y(), world.getBottomY(), world.getTopYInclusive());
			List<Waypoint> list = new ArrayList<>(stack.getOrDefault(ModComponents.WAYPOINTS, List.of()));
			list.removeIf(w -> w.name().equalsIgnoreCase(name));
			list.add(new Waypoint(name, payload.x(), y, payload.z()));
			stack.set(ModComponents.WAYPOINTS, list);
			ServerPlayNetworking.send(player, new DriveWaypointListPayload(list));
		});

		ServerPlayNetworking.registerGlobalReceiver(DeleteDriveWaypointPayload.ID, (payload, context) -> {
			ServerPlayerEntity player = context.player();
			ItemStack stack = player.getStackInHand(payload.hand());
			if (!stack.isOf(ModItems.USB_DRIVE)) {
				return;
			}
			List<Waypoint> list = new ArrayList<>(stack.getOrDefault(ModComponents.WAYPOINTS, List.of()));
			list.removeIf(w -> w.name().equalsIgnoreCase(payload.name()));
			stack.set(ModComponents.WAYPOINTS, list);
			ServerPlayNetworking.send(player, new DriveWaypointListPayload(list));
		});

		// S2C payload: periodic radar contact/log push while a radar GUI is open.
		PayloadTypeRegistry.playS2C().register(RadarUpdatePayload.ID, RadarUpdatePayload.CODEC);
		PayloadTypeRegistry.playS2C().register(MonitorUpdatePayload.ID, MonitorUpdatePayload.CODEC);

		// C2S payload: an armored door's keypad Submit button.
		PayloadTypeRegistry.playC2S().register(SubmitDoorCodePayload.ID, SubmitDoorCodePayload.CODEC);

		ServerPlayNetworking.registerGlobalReceiver(SubmitDoorCodePayload.ID, (payload, context) -> {
			ServerPlayerEntity player = context.player();
			if (!(player.getEntityWorld() instanceof ServerWorld world)) {
				return;
			}
			if (!player.getBlockPos().isWithinDistance(payload.pos(), 8.0)) {
				return;
			}
			if (!(world.getBlockEntity(payload.pos()) instanceof ArmoredDoorBlockEntity door)) {
				return;
			}

			if (!door.isCodeSet()) {
				door.setCode(payload.code());
				player.sendMessage(Text.translatable("gui.icbmbasics.code_set"), true);
				return;
			}

			if (door.checkCode(payload.code())) {
				BlockState state = world.getBlockState(payload.pos());
				if (state.getBlock() instanceof DoorBlock doorBlock) {
					doorBlock.setOpen(player, world, state, payload.pos(), !state.get(DoorBlock.OPEN));
				}
			} else {
				player.sendMessage(Text.translatable("gui.icbmbasics.wrong_code"), true);
			}
		});

		// C2S payload: the remote detonator GUI's covered red button.
		PayloadTypeRegistry.playC2S().register(TriggerDetonatorPayload.ID, TriggerDetonatorPayload.CODEC);
		// S2C payload: whether that press actually fired the linked charge block.
		PayloadTypeRegistry.playS2C().register(DetonatorResultPayload.ID, DetonatorResultPayload.CODEC);

		ServerPlayNetworking.registerGlobalReceiver(TriggerDetonatorPayload.ID, (payload, context) -> {
			ServerPlayerEntity player = context.player();
			if (!(player.getEntityWorld() instanceof ServerWorld playerWorld)) {
				return;
			}
			ItemStack stack = player.getStackInHand(payload.hand());
			if (!stack.isOf(ModItems.REMOTE_DETONATOR)) {
				return;
			}

			boolean success = false;
			GlobalPos link = stack.get(ModComponents.DETONATOR_LINK);
			if (link != null) {
				ServerWorld targetWorld = playerWorld.getServer().getWorld(link.dimension());
				if (targetWorld != null && targetWorld.isChunkLoaded(link.pos())
						&& targetWorld.getBlockState(link.pos()).getBlock() instanceof DetonatorChargeBlock chargeBlock) {
					chargeBlock.trigger(targetWorld, link.pos());
					success = true;
				}
			}

			ServerPlayNetworking.send(player, new DetonatorResultPayload(success));
		});

		LOGGER.info("ICBM Basics initialized. Explosion power: {}, terrain destruction: {}",
				CONFIG.explosionPower, CONFIG.terrainDestruction);
	}
}
