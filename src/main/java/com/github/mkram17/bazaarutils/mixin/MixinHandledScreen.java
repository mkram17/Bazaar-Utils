// MixinHandledScreen.java
package com.github.mkram17.bazaarutils.mixin;

import com.github.mkram17.bazaarutils.BazaarUtils;
import com.github.mkram17.bazaarutils.Events.SlotClickEvent;
import com.github.mkram17.bazaarutils.Utils.Util;
import com.github.mkram17.bazaarutils.config.BUConfig;
import com.github.mkram17.bazaarutils.features.restrictsell.RestrictSell;
import committee.nova.mkb.api.IKeyBinding;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

//used for SlotClickEvent, register keybinds in chests, block slot clicks
@Mixin(value = HandledScreen.class, priority = 999)
public abstract class MixinHandledScreen {

	@Inject(method = "onMouseClick(Lnet/minecraft/screen/slot/Slot;IILnet/minecraft/screen/slot/SlotActionType;)V",at = @At("HEAD"),cancellable = true)
	private void onHandleMouseClick(Slot slot, int slotId, int button, SlotActionType actionType, CallbackInfo ci) {
		if (slot == null) return;

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

	@Inject(method = "keyPressed", at = @At("HEAD"), cancellable = true)
	public void onkeyPressed(int keyCode, int scanCode, int modifiers, CallbackInfoReturnable<Boolean> cir) {
		IKeyBinding iKeyBinding = BUConfig.get().stashHelper.getStashExtended();
		KeyBinding keyBinding = iKeyBinding.getKeyBinding();
		if (keyBinding.matchesKey(keyCode, scanCode) && iKeyBinding.getKeyModifier().isActive(iKeyBinding.getKeyConflictContext())) {
			iKeyBinding.press();
			keyBinding.setPressed(true);
		}
	}
	@Inject(method = "onMouseClick(Lnet/minecraft/screen/slot/Slot;IILnet/minecraft/screen/slot/SlotActionType;)V", at = @At("HEAD"), cancellable = true)
	public void onMouseClickedSlot(Slot slot, int slotId, int button, SlotActionType actionType, CallbackInfo ci) {
		RestrictSell sell = BUConfig.get().restrictSell;
		if(sell.isSlotLocked(slotId)){
			if(sell.getSafetyClicks() < 3) {
				sell.addSafetyClick();
				Util.notifyAll(sell.getMessage());
				ci.cancel();
			} else{
				sell.resetSafetyClicks();
			}
        }
	}
}