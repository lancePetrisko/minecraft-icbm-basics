package com.example.icbmbasics.registry;

import com.example.icbmbasics.ICBMBasics;
import com.example.icbmbasics.network.LauncherScreenData;
import com.example.icbmbasics.network.UsbDriveScreenData;
import com.example.icbmbasics.screen.MissileLauncherScreenHandler;
import com.example.icbmbasics.screen.UsbDriveScreenHandler;

import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerType;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.screen.ScreenHandlerType;

public final class ModScreenHandlers {
	public static final ScreenHandlerType<MissileLauncherScreenHandler> MISSILE_LAUNCHER =
			Registry.register(Registries.SCREEN_HANDLER, ICBMBasics.id("missile_launcher"),
					new ExtendedScreenHandlerType<>(MissileLauncherScreenHandler::new, LauncherScreenData.PACKET_CODEC));

	public static final ScreenHandlerType<UsbDriveScreenHandler> USB_DRIVE =
			Registry.register(Registries.SCREEN_HANDLER, ICBMBasics.id("usb_drive"),
					new ExtendedScreenHandlerType<>(UsbDriveScreenHandler::new, UsbDriveScreenData.PACKET_CODEC));

	private ModScreenHandlers() {
	}

	public static void register() {
	}
}
