// MixinHandledScreen.java
package com.github.mkram17.bazaarutils.mixin;

import com.github.mkram17.bazaarutils.BazaarUtils;
import com.github.mkram17.bazaarutils.events.minecraft.SlotClickEvent;
import com.github.mkram17.bazaarutils.generated.BazaarUtilsModules;
import com.github.mkram17.bazaarutils.utils.bazaar.gui.BazaarScreenType;
import com.github.mkram17.bazaarutils.utils.minecraft.gui.ScreenManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.network.chat.Component;
import net.minecraft.data.AtlasIds;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

//used for SlotClickEvent, register keybinds in chests, block slot clicks, highlighting slots
@Mixin(value = AbstractContainerScreen.class, priority = 999)
public abstract class MixinAbstractContainerScreen extends Screen {
	protected MixinAbstractContainerScreen(Component title) {
		super(title);
	}

	@Inject(method = "slotClicked(Lnet/minecraft/world/inventory/Slot;IILnet/minecraft/world/inventory/ClickType;)V", at = @At("HEAD"), cancellable = true)
	private void onHandleMouseClick(Slot slot, int slotId, int button, ClickType actionType, CallbackInfo ci) {
		if (slot == null) return;

		AbstractContainerScreen<?> screen = (AbstractContainerScreen<?>) (Object) this;
		SlotClickEvent event = new SlotClickEvent(screen, slot, slotId, button, actionType);
		BazaarUtils.EVENT_BUS.post(event);
		// Use the accessor to safely get the client instance
		Minecraft client = ((AccessorScreen) screen).getMinecraft();

		if (event.isCancelled()) {
			ci.cancel();
			return;
		}

		if (event.usePickblockInstead) {
			assert client != null && client.player != null && client.gameMode != null;
            client.gameMode.handleInventoryMouseClick(
					screen.getMenu().containerId,
					slotId,
					2,
					ClickType.PICKUP,
					client.player
			);
			ci.cancel();
		}
	}

	@Inject(method = "renderSlot", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphics;renderItem(Lnet/minecraft/world/item/ItemStack;III)V"))
	private void drawOnItem_OrderStatusHighlight(GuiGraphics context, Slot slot, int x, int y, CallbackInfo ci) {
		if (slot == null || !slot.hasItem() || !BazaarUtilsModules.OrderStatusHighlight.isEnabled()
				|| !ScreenManager.getInstance().isCurrent(BazaarScreenType.ORDERS_PAGE)) {
			return;
		}

		if (Minecraft.getInstance().player != null && slot.container == Minecraft.getInstance().player.getInventory()) {
			return;
		}

		Integer color = BazaarUtilsModules.OrderStatusHighlight.getHighlightColor(slot.getContainerSlot());
		if (color != null) draw(context, slot.x, slot.y, BazaarUtilsModules.OrderStatusHighlight.getIdentifier(), color);
	}

	@Inject(method = "renderSlot", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphics;renderItem(Lnet/minecraft/world/item/ItemStack;III)V"))
	private void drawOnItem_InstantSellHighlight(GuiGraphics context, Slot slot, int x, int y, CallbackInfo ci) {
		if (slot == null || !slot.hasItem() || !BazaarUtilsModules.InstantSellHighlight.isEnabled()
				|| !ScreenManager.getInstance().isCurrent(BazaarScreenType.MAIN_PAGE, BazaarScreenType.PRODUCTS_CATALOG_PAGE, BazaarScreenType.PRODUCT_PAGE)) {
			return;
		}

		if (Minecraft.getInstance().player != null && slot.container != Minecraft.getInstance().player.getInventory()) {
			return;
		}

		Integer color = BazaarUtilsModules.InstantSellHighlight.getHighlightColor(slot.getContainerSlot());
		if (color != null) draw(context, slot.x, slot.y, BazaarUtilsModules.InstantSellHighlight.getIdentifier(), color);
	}

	@Unique
	protected void draw(GuiGraphics context, int x, int y, Identifier identifier, int argb) {
		final var sprite = Minecraft.getInstance().getAtlasManager()
				.getAtlasOrThrow(AtlasIds.GUI)
				.getSprite(identifier);

		context.blitSprite(RenderPipelines.GUI_TEXTURED, sprite, x, y, 16, 16, argb);
	}
}