// MixinHandledScreen.java
package com.github.mkram17.bazaarutils.mixin;

import com.github.mkram17.bazaarutils.BazaarUtils;
import com.github.mkram17.bazaarutils.events.minecraft.SlotInteractionEvent;
import com.github.mkram17.bazaarutils.generated.BazaarUtilsModules;
import com.github.mkram17.bazaarutils.utils.bazaar.gui.BazaarScreenType;
import com.github.mkram17.bazaarutils.utils.minecraft.SlotHighlight;
import com.github.mkram17.bazaarutils.utils.minecraft.gui.ScreenManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
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

	// SkyblockAPI's SlotClickEvent is mouse-only (posted from ScreenMouseClickEvent against the
	// hovered slot). slotClicked is the single vanilla chokepoint every interaction path routes
	// through — mouse clicks, number-key hotbar swaps, the drop key, double-click — so posting the
	// cancellable SlotInteractionEvent here keeps the insta-sell / sell-sacks safety gate covering
	// keyboard-driven sells, which the mouse-only event silently missed.
	@Inject(method = "slotClicked(Lnet/minecraft/world/inventory/Slot;IILnet/minecraft/world/inventory/ClickType;)V", at = @At("HEAD"), cancellable = true)
	private void onSlotClicked_RestrictionGate(Slot slot, int slotId, int button, ClickType clickType, CallbackInfo ci) {
		if (slot == null) return;

		AbstractContainerScreen<?> screen = (AbstractContainerScreen<?>) (Object) this;
		if (new SlotInteractionEvent(screen, slot, slotId, button, clickType).post(BazaarUtils.EVENT_BUS)) {
			ci.cancel();
		}
	}

	@Inject(method = "renderSlot", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphics;renderItem(Lnet/minecraft/world/item/ItemStack;III)V"))
	private void drawOnItem_OrderStatusHighlight(GuiGraphics context, Slot slot, int x, int y, CallbackInfo ci) {
		// Orders live in the chest, so the player's own slots are not ours to tint.
		drawHighlight(context, slot, BazaarUtilsModules.OrderStatusHighlight,
				BazaarUtilsModules.OrderStatusHighlight.isEnabled()
						&& ScreenManager.getInstance().isCurrent(BazaarScreenType.ORDERS_PAGE),
				false);
	}

	@Inject(method = "renderSlot", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphics;renderItem(Lnet/minecraft/world/item/ItemStack;III)V"))
	private void drawOnItem_InstantSellHighlight(GuiGraphics context, Slot slot, int x, int y, CallbackInfo ci) {
		// The reverse: what an instant sell would consume is held by the player, not the chest.
		drawHighlight(context, slot, BazaarUtilsModules.InstantSellHighlight,
				BazaarUtilsModules.InstantSellHighlight.isEnabled()
						&& ScreenManager.getInstance().isCurrent(BazaarScreenType.MAIN_PAGE, BazaarScreenType.PRODUCTS_CATALOG_PAGE, BazaarScreenType.PRODUCT_PAGE),
				true);
	}

	/**
	 * Tints one slot if {@code highlight} has a colour for it. {@code onPlayerInventory} picks
	 * which half of the screen the highlight owns — the two callers want opposite halves, and
	 * their slot indices overlap, so getting this wrong tints unrelated items.
	 */
	@Unique
	private void drawHighlight(GuiGraphics context, Slot slot, SlotHighlight highlight, boolean applies, boolean onPlayerInventory) {
		if (slot == null || !slot.hasItem() || !applies) return;

		LocalPlayer player = Minecraft.getInstance().player;
		if (player != null && (slot.container == player.getInventory()) != onPlayerInventory) return;

		Integer color = highlight.getHighlightColor(slot.getContainerSlot());
		if (color != null) draw(context, slot.x, slot.y, highlight.getIdentifier(), color);
	}

	@Unique
	protected void draw(GuiGraphics context, int x, int y, Identifier identifier, int argb) {
		final var sprite = Minecraft.getInstance().getAtlasManager()
				.getAtlasOrThrow(AtlasIds.GUI)
				.getSprite(identifier);

		context.blitSprite(RenderPipelines.GUI_TEXTURED, sprite, x, y, 16, 16, argb);
	}
}