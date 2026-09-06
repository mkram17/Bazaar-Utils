// MixinHandledScreen.java
package com.github.mkram17.bazaarutils.mixin;

import com.github.mkram17.bazaarutils.BazaarUtils;
import com.github.mkram17.bazaarutils.events.minecraft.SlotInteractionEvent;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

//used for SlotClickEvent, register keybinds in chests, block slot clicks
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
	@Inject(method = "slotClicked(Lnet/minecraft/world/inventory/Slot;IILnet/minecraft/world/inventory/ContainerInput;)V", at = @At("HEAD"), cancellable = true)
	private void onSlotClicked_RestrictionGate(Slot slot, int slotId, int button, ContainerInput actionType, CallbackInfo ci) {
		if (slot == null) return;

		AbstractContainerScreen<?> screen = (AbstractContainerScreen<?>) (Object) this;
		if (new SlotInteractionEvent(screen, slot, slotId, button, actionType).post(BazaarUtils.EVENT_BUS)) {
			ci.cancel();
		}
	}
}
