package com.github.mkram17.bazaarutils.utils.minecraft;

import com.github.mkram17.bazaarutils.utils.bazaar.data.BazaarDataManager;
import com.github.mkram17.bazaarutils.utils.minecraft.gui.ScreenManager;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.GenericContainerScreen;
import net.minecraft.item.ItemStack;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

public final class PlayerSlots {

    private PlayerSlots() {}

    public static Optional<Integer> findScreenSlotByProductId(String productId) {
        return ScreenManager.getInstance()
                .current()
                .flatMap(context -> context.as(GenericContainerScreen.class))
                .flatMap(screen -> {
                    int containerSize = screen.getScreenHandler().getInventory().size();
                    List<ItemStack> mainStacks = Objects.requireNonNull(MinecraftClient.getInstance().player)
                            .getInventory().getMainStacks();

                    for (int i = 0; i < mainStacks.size(); i++) {
                        ItemStack stack = mainStacks.get(i);

                        boolean matches = !stack.isEmpty()
                                && BazaarDataManager.findProductIdOptional(stack.getName().getString())
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