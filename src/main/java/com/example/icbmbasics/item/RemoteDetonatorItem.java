package com.example.icbmbasics.item;

import com.example.icbmbasics.block.DetonatorChargeBlock;
import com.example.icbmbasics.network.DetonatorScreenData;
import com.example.icbmbasics.registry.ModComponents;
import com.example.icbmbasics.screen.DetonatorScreenHandler;

import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerFactory;
import net.minecraft.block.BlockState;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.math.GlobalPos;
import net.minecraft.world.World;

/**
 * Right-click a {@code DetonatorChargeBlock} to bind this specific stack to
 * it (stored as {@link ModComponents#DETONATOR_LINK}, vanilla's own
 * dimension+pos pair - same shape as a lodestone compass's tracker). Plain
 * right-click anywhere opens a GUI with a covered launch button; pressing it
 * fires that charge block's redstone pulse no matter where the player
 * currently is.
 */
public class RemoteDetonatorItem extends Item {
	public RemoteDetonatorItem(Settings settings) {
		super(settings);
	}

	@Override
	public ActionResult useOnBlock(ItemUsageContext context) {
		World world = context.getWorld();
		if (world.isClient()) {
			return ActionResult.SUCCESS;
		}

		BlockState state = world.getBlockState(context.getBlockPos());
		if (!(state.getBlock() instanceof DetonatorChargeBlock)) {
			return ActionResult.PASS;
		}

		ItemStack stack = context.getStack();
		stack.set(ModComponents.DETONATOR_LINK, GlobalPos.create(world.getRegistryKey(), context.getBlockPos()));

		PlayerEntity player = context.getPlayer();
		if (player != null) {
			player.sendMessage(Text.translatable("message.icbmbasics.detonator_linked",
					context.getBlockPos().getX(), context.getBlockPos().getY(), context.getBlockPos().getZ()), true);
		}

		return ActionResult.SUCCESS;
	}

	@Override
	public ActionResult use(World world, PlayerEntity player, Hand hand) {
		if (!world.isClient() && player instanceof ServerPlayerEntity serverPlayer) {
			boolean linked = serverPlayer.getStackInHand(hand).contains(ModComponents.DETONATOR_LINK);
			serverPlayer.openHandledScreen(new ExtendedScreenHandlerFactory<DetonatorScreenData>() {
				@Override
				public Text getDisplayName() {
					return Text.translatable("item.icbmbasics.remote_detonator");
				}

				@Override
				public ScreenHandler createMenu(int syncId, PlayerInventory playerInventory, PlayerEntity menuPlayer) {
					return new DetonatorScreenHandler(syncId, hand, menuPlayer, linked);
				}

				@Override
				public DetonatorScreenData getScreenOpeningData(ServerPlayerEntity opener) {
					return new DetonatorScreenData(hand, linked);
				}
			});
		}
		return ActionResult.SUCCESS;
	}
}
