// MixinHandledScreen.java
package com.github.mkram17.bazaarutils.mixin;

import com.github.mkram17.bazaarutils.BazaarUtils;
import com.github.mkram17.bazaarutils.config.BUConfig;
import com.github.mkram17.bazaarutils.events.SlotClickEvent;
import com.github.mkram17.bazaarutils.features.OrderStatusHighlight;
import com.github.mkram17.bazaarutils.features.StashHelper;
import com.github.mkram17.bazaarutils.features.restrictsell.RestrictSell;
import com.github.mkram17.bazaarutils.misc.BUCompatibilityHelper;
import com.github.mkram17.bazaarutils.misc.ItemSlotButtonWidget;
import com.github.mkram17.bazaarutils.misc.orderinfo.OrderData;
import com.github.mkram17.bazaarutils.utils.Util;
import com.moulberry.mixinconstraints.annotations.IfModLoaded;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.text.Text;
import net.minecraft.util.math.ColorHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
//? if > 1.21.5 {
import net.minecraft.client.gl.RenderPipelines;
//?} else {
/*import net.minecraft.client.render.RenderLayer;
*///?}

//used for SlotClickEvent, register keybinds in chests, block slot clicks
@Mixin(value = HandledScreen.class, priority = 999)
public abstract class MixinHandledScreen<T extends ScreenHandler> extends Screen {


	protected MixinHandledScreen(Text title) {
		super(title);
	}

	@Inject(method = "onMouseClick(Lnet/minecraft/screen/slot/Slot;IILnet/minecraft/screen/slot/SlotActionType;)V", at = @At("HEAD"), cancellable = true)
	private void onHandleMouseClick(Slot slot, int slotId, int button, SlotActionType actionType, CallbackInfo ci) {
		if (slot == null) return;

		//for insta sell rules
		RestrictSell sell = BUConfig.get().restrictSell;
		if (sell.isSlotLocked(slotId)) {
			if (sell.getSafetyClicks() < 3) {
				sell.addSafetyClick();
				Util.notifyAll(sell.getMessage());
				ci.cancel();
			} else {
				sell.resetSafetyClicks();
			}
		}

		HandledScreen<?> screen = (HandledScreen<?>) (Object) this;
		SlotClickEvent event = new SlotClickEvent(screen, slot, slotId, button, actionType);
		BazaarUtils.eventBus.post(event);
//		Util.notifyAll("Mouse Click Posted");
// Use the accessor to safely get the client instance
		MinecraftClient client = ((AccessorScreen) screen).getClient();
		if (event.isCancelled()) {
			ci.cancel();
			return;
		}

		if (event.usePickblockInstead) {
			assert client != null && client.player != null;
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

	@IfModLoaded(BUCompatibilityHelper.AMECS_MODID)
	@Inject(method = "keyPressed", at = @At("HEAD"), cancellable = true)
	public void onkeyPressed(int keyCode, int scanCode, int modifiers, CallbackInfoReturnable<Boolean> cir) {
		StashHelper keyBinding = (StashHelper) BazaarUtils.keybinds.getFirst();
		if (!keyBinding.isPressed() && keyBinding.getDefaultKey().getCode() == keyCode && keyBinding.getDefaultModifiers().getAlt()) {
//			Util.notifyAll("Stash helper pressed", Util.notificationTypes.FEATURE);
			if (keyBinding.getTicksBetweenPresses() > 10)
				keyBinding.setPressed(true);
			cir.setReturnValue(true);
		}
	}

	@Inject(method = "init", at = @At("TAIL"))
	private void bazaarutils$addConfiguredButtons(CallbackInfo ci) {


		int buttonsAdded = 0;
		for (ItemSlotButtonWidget button : BUConfig.getWidgets()) {
			this.addDrawableChild(button);
			buttonsAdded++;
		}
	}

	@Inject(method = "drawSlot", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/DrawContext;drawItem(Lnet/minecraft/item/ItemStack;III)V"))
	private void bazaarutils$drawOnItem(DrawContext context, Slot slot, CallbackInfo ci) {
		if (slot == null || !BUConfig.get().orderStatusHighlight.isEnabled() || !slot.hasStack())
			return;
		if (MinecraftClient.getInstance().player != null && slot.inventory == MinecraftClient.getInstance().player.getInventory())
			return;

		OrderData.statuses orderStatus = OrderStatusHighlight.getHighlightType(slot.getIndex());

		if (orderStatus == OrderData.statuses.COMPETITIVE) {
			draw(context, slot.x, slot.y, OrderData.statuses.COMPETITIVE);
		} else if (orderStatus == OrderData.statuses.OUTDATED) {
			draw(context, slot.x, slot.y, OrderData.statuses.OUTDATED);
		} else if (orderStatus == OrderData.statuses.MATCHED) {
			draw(context, slot.x, slot.y, OrderData.statuses.MATCHED);
		} else if (orderStatus == OrderData.statuses.FILLED) {
			draw(context, slot.x, slot.y, OrderData.statuses.FILLED);
		}
	}


	@Unique
	protected void draw(DrawContext context, int x, int y, OrderData.statuses orderStatus) {
		//? if > 1.21.5 {
		if (orderStatus == OrderData.statuses.COMPETITIVE) {
			context.drawSpriteStretched(RenderPipelines.GUI_TEXTURED,
					MinecraftClient.getInstance()
							.getGuiAtlasManager()
							.getSprite(OrderStatusHighlight.IDENTIFIER)
					, x, y, 16, 16,
					ColorHelper.fromFloats(OrderStatusHighlight.BACKGROUND_TRANSPARENCY, 0.0f, 1.0f, 0.0f)
			);
		} else if (orderStatus == OrderData.statuses.OUTDATED) {
			context.drawSpriteStretched(RenderPipelines.GUI_TEXTURED,
					MinecraftClient.getInstance()
							.getGuiAtlasManager()
							.getSprite(OrderStatusHighlight.IDENTIFIER)
					, x, y, 16, 16,
					ColorHelper.fromFloats(OrderStatusHighlight.BACKGROUND_TRANSPARENCY, 1.0f, 0.0f, 0.0f)
			);
		} else if (orderStatus == OrderData.statuses.FILLED) {
			context.drawSpriteStretched(RenderPipelines.GUI_TEXTURED,
					MinecraftClient.getInstance()
							.getGuiAtlasManager()
							.getSprite(OrderStatusHighlight.IDENTIFIER)
					, x, y, 16, 16,
					ColorHelper.fromFloats(OrderStatusHighlight.BACKGROUND_TRANSPARENCY, 0.0f, 1.0f, 0.0f)
			);
		} else {
			context.drawSpriteStretched(RenderPipelines.GUI_TEXTURED,
					MinecraftClient.getInstance()
							.getGuiAtlasManager()
							.getSprite(OrderStatusHighlight.IDENTIFIER)
					, x, y, 16, 16,
					ColorHelper.fromFloats(OrderStatusHighlight.BACKGROUND_TRANSPARENCY, 1.0f, 1.0f, 0.0f)
			);
		}
		//?} else {
		/*if (orderStatus == OrderData.statuses.COMPETITIVE) {
			context.drawSpriteStretched(RenderLayer::getGuiTextured,
					MinecraftClient.getInstance()
							.getGuiAtlasManager()
							.getSprite(OrderStatusHighlight.IDENTIFIER)
					, x, y, 16, 16,
					ColorHelper.fromFloats(OrderStatusHighlight.BACKGROUND_TRANSPARENCY, 0.0f, 1.0f, 0.0f)
			);
		} else if(orderStatus == OrderData.statuses.OUTDATED) {
			context.drawSpriteStretched(RenderLayer::getGuiTextured,
					MinecraftClient.getInstance()
							.getGuiAtlasManager()
							.getSprite(OrderStatusHighlight.IDENTIFIER)
					, x, y, 16, 16,
					ColorHelper.fromFloats(OrderStatusHighlight.BACKGROUND_TRANSPARENCY, 1.0f, 0.0f, 0.0f)
			);
		} else {
			context.drawSpriteStretched(RenderLayer::getGuiTextured,
					MinecraftClient.getInstance()
							.getGuiAtlasManager()
							.getSprite(OrderStatusHighlight.IDENTIFIER)
					, x, y, 16, 16,
					ColorHelper.fromFloats(OrderStatusHighlight.BACKGROUND_TRANSPARENCY, 1.0f, 1.0f, 0.0f)
			);
		}
		*///?}
	}

}
