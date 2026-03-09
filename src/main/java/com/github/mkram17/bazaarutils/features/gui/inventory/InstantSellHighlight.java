package com.github.mkram17.bazaarutils.features.gui.inventory;

import com.github.mkram17.bazaarutils.BazaarUtils;
import com.github.mkram17.bazaarutils.config.features.gui.InventoryConfig;
import com.github.mkram17.bazaarutils.events.ChestLoadedEvent;
import com.github.mkram17.bazaarutils.events.listener.BUListener;
import com.github.mkram17.bazaarutils.utils.Util;
import com.github.mkram17.bazaarutils.utils.bazaar.gui.BazaarScreenHandler;
import com.github.mkram17.bazaarutils.utils.bazaar.gui.BazaarScreens;
import com.github.mkram17.bazaarutils.utils.annotations.modules.Module;
import com.github.mkram17.bazaarutils.utils.bazaar.components.InstantSellParser;
import com.github.mkram17.bazaarutils.utils.bazaar.market.order.OrderInfo;
import com.github.mkram17.bazaarutils.utils.config.BUToggleableFeature;
import com.github.mkram17.bazaarutils.utils.minecraft.ItemInfo;
import com.github.mkram17.bazaarutils.utils.minecraft.SlotHighlight;
import com.github.mkram17.bazaarutils.utils.minecraft.gui.ScreenContext;
import com.github.mkram17.bazaarutils.utils.minecraft.gui.ScreenManager;
import meteordevelopment.orbit.EventHandler;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.screen.slot.Slot;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Module
public class InstantSellHighlight extends BUListener implements BUToggleableFeature, SlotHighlight {
    public static final Identifier IDENTIFIER = Identifier.tryParse(BazaarUtils.MOD_ID, "highlights/standard_background");

    @Override
    public Identifier getIdentifier() {
        return IDENTIFIER;
    }

    private static final Map<Integer, Integer> colorCache = new ConcurrentHashMap<>();

    private void populateCache(Set<String> names, HandledScreen<?> screen, PlayerInventory playerInventory) {
        colorCache.clear();

        for (Slot slot : screen.getScreenHandler().slots) {
            if (!slot.hasStack() || slot.inventory != playerInventory) continue;

            Text customName = slot.getStack().getCustomName();

            if (customName == null) continue;

            String itemName = customName.getString();

            if (names.stream().anyMatch(itemName::equalsIgnoreCase)) {
                colorCache.put(slot.getIndex(), InventoryConfig.INSTANT_SELL_HIGHLIGHT_COLOR);
            }
        }
    }

    @Override
    public Integer getHighlightColor(int slotIndex) {
        return colorCache.get(slotIndex);
    }

    @Override
    public boolean isEnabled() {
        return InventoryConfig.INSTANT_SELL_HIGHLIGHT_TOGGLE;
    }

    public InstantSellHighlight() {
        super();
    }

    @Override
    protected void registerFabricEvents() {
        ScreenEvents.AFTER_INIT.register(this::onScreenInitialized);
    }

    @EventHandler
    private void onChestLoaded(ChestLoadedEvent event) {
        colorCache.clear();

        if (!isEnabled()) return;

        ScreenManager.getInstance().current().ifPresent(context -> {
            HandledScreen<?> screen = ScreenManager.getCurrentlyHandledScreen(HandledScreen.class).orElse(null);
            MinecraftClient client = MinecraftClient.getInstance();

            if (screen == null || client.player == null) return;

            List<OrderInfo> orders = resolveOrders(context);

            if (orders.isEmpty()) return;

            Set<String> names = orders.stream()
                    .map(OrderInfo::getName)
                    .collect(Collectors.toSet());

            populateCache(names, screen, client.player.getInventory());
        });
    }

    private void onScreenInitialized(MinecraftClient client, Screen screen, int width, int height) {
        colorCache.clear();
    }

    private static List<OrderInfo> resolveOrders(ScreenContext context) {
        if (context.isAnyOf(BazaarScreens.ITEM_PAGE))
            return BazaarScreenHandler.getInstantSellItem(context)
                    .map(ItemInfo::itemStack)
                    .flatMap(InstantSellParser::parseItemPageOrder)
                    .map(InstantSellParser.InstantSellResult::items)
                    .orElse(List.of());

        return BazaarScreenHandler.getInstantSellItem(context)
                .map(ItemInfo::itemStack)
                .map(InstantSellParser::parseOrders)
                .map(InstantSellParser.InstantSellResult::items)
                .orElse(List.of());
    }
}