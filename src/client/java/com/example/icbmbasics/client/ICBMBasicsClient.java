package com.example.icbmbasics.client;

import com.example.icbmbasics.client.screen.ArmoredDoorScreen;
import com.example.icbmbasics.client.screen.MissileLauncherScreen;
import com.example.icbmbasics.client.screen.RadarScreen;
import com.example.icbmbasics.client.screen.UsbDriveScreen;
import com.example.icbmbasics.network.DriveWaypointListPayload;
import com.example.icbmbasics.network.LauncherWaypointListPayload;
import com.example.icbmbasics.network.RadarUpdatePayload;
import com.example.icbmbasics.registry.ModEntities;
import com.example.icbmbasics.registry.ModScreenHandlers;
import com.example.icbmbasics.screen.MissileLauncherScreenHandler;
import com.example.icbmbasics.screen.RadarScreenHandler;
import com.example.icbmbasics.screen.UsbDriveScreenHandler;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.minecraft.client.gui.screen.ingame.HandledScreens;
import net.minecraft.client.render.entity.FlyingItemEntityRenderer;

public class ICBMBasicsClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		// The missile renders as its (oversized) item, like a snowball on steroids.
		// Swap this for a custom EntityModel later if you want a fancier missile.
		EntityRendererRegistry.register(ModEntities.MISSILE,
				ctx -> new FlyingItemEntityRenderer<>(ctx, 2.5f, true));
		// SAM interceptors reuse the same missile item, just smaller.
		EntityRendererRegistry.register(ModEntities.SAM_INTERCEPTOR,
				ctx -> new FlyingItemEntityRenderer<>(ctx, 1.5f, true));

		HandledScreens.register(ModScreenHandlers.MISSILE_LAUNCHER, MissileLauncherScreen::new);
		HandledScreens.register(ModScreenHandlers.USB_DRIVE, UsbDriveScreen::new);
		HandledScreens.register(ModScreenHandlers.RADAR, RadarScreen::new);
		HandledScreens.register(ModScreenHandlers.ARMORED_DOOR, ArmoredDoorScreen::new);

		// Refreshes the open launcher GUI's own waypoint list after a save/delete.
		// The slotted drive's list needs no such payload - it rides along on the
		// USB slot's synced ItemStack.
		ClientPlayNetworking.registerGlobalReceiver(LauncherWaypointListPayload.ID, (payload, context) ->
				context.client().execute(() -> {
					if (context.player().currentScreenHandler instanceof MissileLauncherScreenHandler handler) {
						handler.setLauncherWaypoints(payload.launcherWaypoints());
					}
				}));

		// Refreshes the open USB drive GUI's waypoint list after a save/delete.
		ClientPlayNetworking.registerGlobalReceiver(DriveWaypointListPayload.ID, (payload, context) ->
				context.client().execute(() -> {
					if (context.player().currentScreenHandler instanceof UsbDriveScreenHandler handler) {
						handler.setWaypoints(payload.waypoints());
					}
				}));

		// Refreshes the open radar GUI's contacts/log every scan interval.
		ClientPlayNetworking.registerGlobalReceiver(RadarUpdatePayload.ID, (payload, context) ->
				context.client().execute(() -> {
					if (context.player().currentScreenHandler instanceof RadarScreenHandler handler
							&& handler.getRadarPos().equals(payload.pos())) {
						handler.setContacts(payload.contacts());
						handler.setLog(payload.log());
					}
				}));
	}
}
