package com.example.icbmbasics.client;

import com.example.icbmbasics.client.render.MissileEntityRenderer;
import com.example.icbmbasics.client.render.MonitorBlockEntityRenderer;
import com.example.icbmbasics.client.render.MonitorRenderData;
import com.example.icbmbasics.client.screen.ArmoredDoorScreen;
import com.example.icbmbasics.client.screen.CiwsAmmoScreen;
import com.example.icbmbasics.client.screen.MissileLauncherScreen;
import com.example.icbmbasics.client.screen.RadarScreen;
import com.example.icbmbasics.client.screen.SamAmmoScreen;
import com.example.icbmbasics.client.screen.UsbDriveScreen;
import com.example.icbmbasics.network.DriveWaypointListPayload;
import com.example.icbmbasics.network.LauncherWaypointListPayload;
import com.example.icbmbasics.network.MonitorUpdatePayload;
import com.example.icbmbasics.network.RadarUpdatePayload;
import com.example.icbmbasics.registry.ModBlockEntities;
import com.example.icbmbasics.registry.ModEntities;
import com.example.icbmbasics.registry.ModScreenHandlers;
import com.example.icbmbasics.screen.MissileLauncherScreenHandler;
import com.example.icbmbasics.screen.RadarScreenHandler;
import com.example.icbmbasics.screen.UsbDriveScreenHandler;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.BlockEntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.minecraft.client.gui.screen.ingame.HandledScreens;

public class ICBMBasicsClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		// The missile renders as its (oversized) item, oriented along its flight
		// path rather than billboarding to the camera - see MissileEntityRenderer.
		// Swap this for a custom EntityModel later if you want a fancier missile.
		EntityRendererRegistry.register(ModEntities.MISSILE,
				ctx -> new MissileEntityRenderer<>(ctx, 2.5f));
		// SAM interceptors reuse the same missile item, just smaller.
		EntityRendererRegistry.register(ModEntities.SAM_INTERCEPTOR,
				ctx -> new MissileEntityRenderer<>(ctx, 1.5f));
		// CIWS tracer rounds render as a small flying gray-concrete "bullet",
		// same renderer, just a plain block item instead of the missile item.
		EntityRendererRegistry.register(ModEntities.CIWS_BULLET,
				ctx -> new MissileEntityRenderer<>(ctx, 0.3f));

		HandledScreens.register(ModScreenHandlers.MISSILE_LAUNCHER, MissileLauncherScreen::new);
		HandledScreens.register(ModScreenHandlers.USB_DRIVE, UsbDriveScreen::new);
		HandledScreens.register(ModScreenHandlers.RADAR, RadarScreen::new);
		HandledScreens.register(ModScreenHandlers.ARMORED_DOOR, ArmoredDoorScreen::new);
		HandledScreens.register(ModScreenHandlers.SAM_SITE, SamAmmoScreen::new);
		HandledScreens.register(ModScreenHandlers.CIWS, CiwsAmmoScreen::new);

		// Monitors are a plain always-on world display, not a GUI - see MonitorBlockEntityRenderer.
		BlockEntityRendererRegistry.register(ModBlockEntities.MONITOR, MonitorBlockEntityRenderer::new);

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

		// Refreshes a monitor's cached snapshot - not tied to any open screen, since
		// monitors render constantly in the world regardless of GUIs.
		ClientPlayNetworking.registerGlobalReceiver(MonitorUpdatePayload.ID, (payload, context) ->
				context.client().execute(() ->
						MonitorRenderData.put(payload.monitorPos(), payload.radarPos(),
								payload.detectionRadius(), payload.contacts())));
	}
}
