package com.example.icbmbasics.registry;

import com.example.icbmbasics.ICBMBasics;
import com.example.icbmbasics.network.LauncherScreenData;
import com.example.icbmbasics.screen.MissileLauncherScreenHandler;

import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerType;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.screen.ScreenHandlerType;

public final class ModScreenHandlers {
	public static final ScreenHandlerType<MissileLauncherScreenHandler> MISSILE_LAUNCHER =
			Registry.register(Registries.SCREEN_HANDLER, ICBMBasics.id("missile_launcher"),
					new ExtendedScreenHandlerType<>(MissileLauncherScreenHandler::new, LauncherScreenData.PACKET_CODEC));

	private ModScreenHandlers() {
	}

	public static void register() {
	}
}
