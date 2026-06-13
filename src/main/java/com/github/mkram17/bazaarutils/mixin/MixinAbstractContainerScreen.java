// MixinHandledScreen.java
package com.github.mkram17.bazaarutils.mixin;

import com.github.mkram17.bazaarutils.BazaarUtils;
import com.github.mkram17.bazaarutils.config.BUConfig;
import com.github.mkram17.bazaarutils.events.SlotClickEvent;
import com.github.mkram17.bazaarutils.features.OrderStatusHighlight;
import com.github.mkram17.bazaarutils.features.restrictsell.RestrictSell;
import com.github.mkram17.bazaarutils.misc.orderinfo.BazaarOrder;
import com.github.mkram17.bazaarutils.misc.orderinfo.OrderInfoContainer;
import com.github.mkram17.bazaarutils.utils.PlayerActionUtil;
import com.github.mkram17.bazaarutils.utils.ScreenInfo;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.network.chat.Component;
import net.minecraft.data.AtlasIds;
import net.minecraft.util.ARGB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

//used for SlotClickEvent, register keybinds in chests, block slot clicks
@Mixin(value = AbstractContainerScreen.class, priority = 999)
public abstract class MixinAbstractContainerScreen extends Screen {

	protected MixinAbstractContainerScreen(Component title) {
		super(title);
	}

	@Inject(method = "slotClicked(Lnet/minecraft/world/inventory/Slot;IILnet/minecraft/world/inventory/ClickType;)V", at = @At("HEAD"), cancellable = true)
	private void onHandleMouseClick(Slot slot, int slotId, int button, ClickType actionType, CallbackInfo ci) {
		if (slot == null){
            return;
        }

		if(shouldCancelClick(slotId)){
            ci.cancel();
        }

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

    @Unique
    private boolean shouldCancelClick(int slotId){
        //for insta sell rules
        RestrictSell sell = BUConfig.get().restrictSell;
        if (sell.isSlotLocked(slotId)) {
            if (sell.getSafetyClicks() < 3) {
                sell.addSafetyClick();
                PlayerActionUtil.notifyAll(sell.getMessage());
                return true;
            } else {
                sell.resetSafetyClicks();
            }
        }
        return false;
    }

	@Inject(method = "init", at = @At("TAIL"))
	private void addConfiguredButtons(CallbackInfo ci) {
		for (AbstractWidget button : BUConfig.getWidgets()) {
			this.addRenderableWidget(button);
		}
	}

	@Inject(method = "renderSlot", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphics;renderItem(Lnet/minecraft/world/item/ItemStack;III)V"))
    //? if > 1.21.10 {
    private void bazaarutils$drawOnItem(GuiGraphics context, Slot slot, int mouseX, int mouseY, CallbackInfo ci) {
    //? } else {
	/*private void drawOnItem(DrawContext context, Slot slot, CallbackInfo ci) {
    *///? }
		ScreenInfo screenInfo = ScreenInfo.getCurrentScreenInfo();
		if (slot == null || !BUConfig.get().orderStatusHighlight.isEnabled() || !screenInfo.inMenu(ScreenInfo.BazaarMenuType.ORDER_SCREEN) || !slot.hasItem())
			return;
		if (Minecraft.getInstance().player != null && slot.container == Minecraft.getInstance().player.getInventory())
			return;

		BazaarOrder order = OrderStatusHighlight.getHighlightedOrder(slot.getContainerSlot());
		if(order == null || order.getFillStatus() == OrderInfoContainer.Statuses.FILLED)
			return;

		draw(context, slot.x, slot.y, order.getOutbidStatus());

	}

	@Unique
	protected void draw(GuiGraphics context, int x, int y, OrderInfoContainer.Statuses orderStatus) {
		final float r, g, b;
		if (orderStatus == OrderInfoContainer.Statuses.COMPETITIVE) {
			r = 0.0f; g = 1.0f; b = 0.0f; // Green
		} else if (orderStatus == OrderInfoContainer.Statuses.OUTBID) {
			r = 1.0f; g = 0.0f; b = 0.0f; // Red
		} else { // MATCHED
			r = 1.0f; g = 1.0f; b = 0.0f; // Yellow
		}

		final int color = ARGB.colorFromFloat(OrderStatusHighlight.BACKGROUND_TRANSPARENCY, r, g, b);

        final var sprite = Minecraft.getInstance().getAtlasManager().getAtlasOrThrow(AtlasIds.GUI)
                .getSprite(OrderStatusHighlight.IDENTIFIER);

		context.blitSprite(RenderPipelines.GUI_TEXTURED,
				sprite, x, y, 16, 16, color
		);
	}

}
