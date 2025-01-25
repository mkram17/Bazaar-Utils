/*
 * Copyright (C) 2022 NotEnoughUpdates contributors
 *
 * This file is part of NotEnoughUpdates.
 *
 * NotEnoughUpdates is free software: you can redistribute it
 * and/or modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation, either
 * version 3 of the License, or (at your option) any later version.
 *
 * NotEnoughUpdates is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with NotEnoughUpdates. If not, see <https://www.gnu.org/licenses/>.
 */

package com.github.sirmegabite.bazaarutils.mixin;

import com.github.sirmegabite.bazaarutils.EventHandlers.SlotClickEvent;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.inventory.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

//thanks neu
@Mixin(value = GuiContainer.class, priority = 500)
public abstract class MixinGuiContainer extends GuiScreen {

	@Inject(method = "handleMouseClick", at = @At(value = "HEAD"), cancellable = true)
	public void handleMouseClick(Slot slotIn, int slotId, int clickedButton, int clickType, CallbackInfo ci) {
		if (slotIn == null) return;
		GuiContainer $this = (GuiContainer) (Object) this;
		SlotClickEvent event = new SlotClickEvent($this, slotIn, slotId, clickedButton, clickType);
		event.post();
		if (event.isCanceled()) {
			ci.cancel();
			return;
		}
		if (event.usePickblockInstead) {
			$this.mc.playerController.windowClick(
					$this.inventorySlots.windowId,
					slotId, 2, 3, $this.mc.thePlayer
			);
			ci.cancel();
		}
	}
}
