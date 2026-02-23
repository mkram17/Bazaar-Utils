package com.github.mkram17.bazaarutils.features.gui.inventory;

import com.github.mkram17.bazaarutils.config.features.gui.InventoryConfig;
import com.github.mkram17.bazaarutils.events.ChestLoadedEvent;
import com.github.mkram17.bazaarutils.events.listener.BUListener;
import com.github.mkram17.bazaarutils.misc.SlotHighlightCache;
import com.github.mkram17.bazaarutils.utils.annotations.modules.Module;
import com.github.mkram17.bazaarutils.utils.bazaar.market.order.OrderInfo;
import com.github.mkram17.bazaarutils.utils.GUIUtils;
import com.github.mkram17.bazaarutils.utils.Util;
import com.github.mkram17.bazaarutils.utils.InstaSellUtil;
import com.github.mkram17.bazaarutils.utils.ScreenInfo;
import com.teamresourceful.resourcefulconfig.api.annotations.ConfigEntry;
import com.teamresourceful.resourcefulconfig.api.annotations.ConfigObject;
import com.teamresourceful.resourcefulconfig.api.annotations.ConfigOption;
import lombok.Getter;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.ColorHelper;

import java.util.*;

@Module
public class InstantSellHighlight extends BUListener {
    private static transient final List<Integer> highlightedSlotIndexes = new ArrayList<>();

    public static boolean isEnabled() {
        return InventoryConfig.INSTANT_SELL_HIGHLIGHT_TOGGLE;
    }

    public InstantSellHighlight() {}

    @EventHandler
    private void onScreenLoad(ChestLoadedEvent e) {
        highlightedSlotIndexes.clear();

        if (!isEnabled() || !ScreenInfo.getCurrentScreenInfo().inMenu(ScreenInfo.BazaarMenuType.BAZAAR_MAIN_PAGE)) {
            return;
        }

        Optional<PlayerInventory> optionalInventory = getInventory();
        if (optionalInventory.isEmpty()) {
            Util.notifyError("Failed to get player inventory.", new Throwable());
            return;
        }
        PlayerInventory inventory = optionalInventory.get();

        List<OrderInfo> instaSellOrders = InstaSellUtil.getInstaSellOrders(e.getItemStacks());
        List<String> names = instaSellOrders.stream()
                .map(OrderInfo::getName)
                .distinct()
                .toList();

        List<ItemStack> inventoryStacks = getInventoryStacks(names);

        highlightedSlotIndexes.addAll(
                inventoryStacks.stream()
                        .filter(itemStack -> !itemStack.isEmpty())
                        .map(itemStack -> GUIUtils.getSlotFromItemStack(inventory, itemStack))
                        .toList()
        );
    }

    private Optional<PlayerInventory> getInventory(){
        ClientPlayerEntity player = MinecraftClient.getInstance().player;
        if (player == null) {
            Util.notifyError("Player is null, cannot get inventory stacks.", new Throwable());
            return Optional.empty();
        }

        return Optional.of(player.getInventory());
    }


    private List<ItemStack> getInventoryStacks(List<String> names){
        List<ItemStack> inventoryStacks = new ArrayList<>();

        var inventoryOpt = getInventory();
        if (inventoryOpt.isEmpty()) return Collections.emptyList();
        var inventory = inventoryOpt.get();

        var stacks = inventory.getMainStacks();

        stacks.forEach(itemStack -> {
            if(itemStack.isEmpty()) return;
            String itemName = itemStack.getName().getString();
            if (names.stream().anyMatch(name -> itemName.toLowerCase().contains(name.toLowerCase()))) {
                inventoryStacks.add(itemStack);
            }
        });
        return inventoryStacks;
    }

    public static void updateHighlightCache() {
        if (!isEnabled()) {
            return;
        }

        for (Integer index : highlightedSlotIndexes) {
            getColorFromIndex(index).ifPresent(instaSellHighlightColor -> SlotHighlightCache.instaSellHighlightCache.computeIfAbsent(index, (k) -> instaSellHighlightColor));
        }
    }

    public static OptionalInt getColorFromIndex(int slotIndex) {
        if (highlightedSlotIndexes.stream().noneMatch(i -> i.equals(slotIndex))) {
            return OptionalInt.empty();
        }

        return OptionalInt.of(InventoryConfig.INSTANT_SELL_HIGHLIGHT_COLOR);
    }
}
