package com.example.icbmbasics.client;

import com.example.icbmbasics.client.screen.MissileLauncherScreen;
import com.example.icbmbasics.network.WaypointListPayload;
import com.example.icbmbasics.registry.ModEntities;
import com.example.icbmbasics.registry.ModScreenHandlers;
import com.example.icbmbasics.screen.MissileLauncherScreenHandler;

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

		HandledScreens.register(ModScreenHandlers.MISSILE_LAUNCHER, MissileLauncherScreen::new);

		// Refreshes the open launcher GUI's waypoint list after a save/delete.
		ClientPlayNetworking.registerGlobalReceiver(WaypointListPayload.ID, (payload, context) ->
				context.client().execute(() -> {
					if (context.player().currentScreenHandler instanceof MissileLauncherScreenHandler handler) {
						handler.setWaypoints(payload.waypoints());
					}
				}));
	}
}
