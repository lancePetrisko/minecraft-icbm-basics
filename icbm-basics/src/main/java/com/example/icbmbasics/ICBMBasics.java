package com.example.icbmbasics;

import com.example.icbmbasics.block.entity.MissileLauncherBlockEntity;
import com.example.icbmbasics.config.ICBMConfig;
import com.example.icbmbasics.network.SetTargetPayload;
import com.example.icbmbasics.registry.ModBlockEntities;
import com.example.icbmbasics.registry.ModBlocks;
import com.example.icbmbasics.registry.ModEntities;
import com.example.icbmbasics.registry.ModItems;
import com.example.icbmbasics.registry.ModScreenHandlers;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
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

		ModItems.register();
		ModBlocks.register();
		ModBlockEntities.register();
		ModEntities.register();
		ModScreenHandlers.register();

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

		LOGGER.info("ICBM Basics initialized. Explosion power: {}, terrain destruction: {}",
				CONFIG.explosionPower, CONFIG.terrainDestruction);
	}
}
