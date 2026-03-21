// MixinHandledScreen.java
package com.github.mkram17.bazaarutils.mixin;

import com.github.mkram17.bazaarutils.BazaarUtils;
import com.github.mkram17.bazaarutils.events.SlotClickEvent;
import com.github.mkram17.bazaarutils.generated.BazaarUtilsModules;
import com.github.mkram17.bazaarutils.utils.bazaar.gui.BazaarScreens;
import com.github.mkram17.bazaarutils.utils.minecraft.gui.ScreenManager;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.text.Text;
import net.minecraft.util.Atlases;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

//used for SlotClickEvent, register keybinds in chests, block slot clicks, highlighting slots
@Mixin(value = HandledScreen.class, priority = 999)
public abstract class MixinHandledScreen extends Screen {
	protected MixinHandledScreen(Text title) {
		super(title);
	}

	@Inject(method = "onMouseClick(Lnet/minecraft/screen/slot/Slot;IILnet/minecraft/screen/slot/SlotActionType;)V", at = @At("HEAD"), cancellable = true)
	private void onHandleMouseClick(Slot slot, int slotId, int button, SlotActionType actionType, CallbackInfo ci) {
		if (slot == null) return;

		HandledScreen<?> screen = (HandledScreen<?>) (Object) this;
		SlotClickEvent event = new SlotClickEvent(screen, slot, slotId, button, actionType);
		BazaarUtils.EVENT_BUS.post(event);
		// Use the accessor to safely get the client instance
		MinecraftClient client = ((AccessorScreen) screen).getClient();

		if (event.isCancelled()) {
			ci.cancel();
			return;
		}

		if (event.usePickblockInstead) {
			assert client != null && client.player != null && client.interactionManager != null;
            client.interactionManager.clickSlot(
					screen.getScreenHandler().syncId,
					slotId,
					2,
					SlotActionType.PICKUP,
					client.player
			);
			ci.cancel();
		}
	}

	@Inject(method = "drawSlot", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/DrawContext;drawItem(Lnet/minecraft/item/ItemStack;III)V"))
	private void drawOnItem_OrderStatusHighlight(DrawContext context, Slot slot, int x, int y, CallbackInfo ci) {
		if (slot == null || !slot.hasStack() || !BazaarUtilsModules.OrderStatusHighlight.isEnabled()
				|| !ScreenManager.getInstance().isCurrent(BazaarScreens.ORDERS_PAGE)) {
			return;
		}

		if (MinecraftClient.getInstance().player != null && slot.inventory == MinecraftClient.getInstance().player.getInventory()) {
			return;
		}

		Integer color = BazaarUtilsModules.OrderStatusHighlight.getHighlightColor(slot.getIndex());
		if (color != null) draw(context, slot.x, slot.y, BazaarUtilsModules.OrderStatusHighlight.getIdentifier(), color);
	}

	@Inject(method = "drawSlot", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/DrawContext;drawItem(Lnet/minecraft/item/ItemStack;III)V"))
	private void drawOnItem_InstantSellHighlight(DrawContext context, Slot slot, int x, int y, CallbackInfo ci) {
		if (slot == null || !slot.hasStack() || !BazaarUtilsModules.InstantSellHighlight.isEnabled()
				|| !ScreenManager.getInstance().isCurrent(BazaarScreens.MAIN_PAGE, BazaarScreens.ITEMS_GROUP_PAGE, BazaarScreens.ITEM_PAGE)) {
			return;
		}

		if (MinecraftClient.getInstance().player != null && slot.inventory != MinecraftClient.getInstance().player.getInventory()) {
			return;
		}

		Integer color = BazaarUtilsModules.InstantSellHighlight.getHighlightColor(slot.getIndex());
		if (color != null) draw(context, slot.x, slot.y, BazaarUtilsModules.InstantSellHighlight.getIdentifier(), color);
	}

	@Unique
	protected void draw(DrawContext context, int x, int y, Identifier identifier, int argb) {
		final var sprite = MinecraftClient.getInstance().getAtlasManager()
				.getAtlasTexture(Atlases.GUI)
				.getSprite(identifier);

		context.drawSpriteStretched(RenderPipelines.GUI_TEXTURED, sprite, x, y, 16, 16, argb);
	}
}