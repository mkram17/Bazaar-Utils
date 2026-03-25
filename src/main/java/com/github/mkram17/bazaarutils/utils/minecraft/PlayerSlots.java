package com.github.mkram17.bazaarutils.utils.minecraft;

import com.github.mkram17.bazaarutils.utils.bazaar.data.BazaarDataUtil;
import com.github.mkram17.bazaarutils.utils.minecraft.gui.ScreenManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

public final class PlayerSlots {

    private PlayerSlots() {}

    public static Optional<Integer> findScreenSlotByProductId(String productId) {
        return ScreenManager.getInstance()
                .current()
                .flatMap(context -> context.as(ContainerScreen.class))
                .flatMap(screen -> {
                    int containerSize = screen.getMenu().getContainer().getContainerSize();
                    List<ItemStack> mainStacks = Objects.requireNonNull(Minecraft.getInstance().player)
                            .getInventory().getNonEquipmentItems();

                    for (int i = 0; i < mainStacks.size(); i++) {
                        ItemStack stack = mainStacks.get(i);

                        boolean matches = !stack.isEmpty()
                                && BazaarDataUtil.findProductIdOptional(stack.getHoverName().getString())
                                .map(id -> id.equals(productId))
                                .orElse(false);

                        if (!matches) continue;

                        int screenSlot = (i < 9)
                                ? containerSize + 27 + i
                                : containerSize + (i - 9);

                        return Optional.of(screenSlot);
                    }

                    return Optional.empty();
                });
    }
}