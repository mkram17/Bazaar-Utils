package com.github.mkram17.bazaarutils.features.gui.inventory;

import com.github.mkram17.bazaarutils.BazaarUtils;
import com.github.mkram17.bazaarutils.config.features.gui.InventoryConfig;
import com.github.mkram17.bazaarutils.data.SellableAPI;
import com.github.mkram17.bazaarutils.events.minecraft.ContainerLoadedEvent;
import com.github.mkram17.bazaarutils.events.BUListener;
import com.github.mkram17.bazaarutils.events.predicates.OnlyBazaarScreen;
import com.github.mkram17.bazaarutils.events.predicates.OnlyWhenEnabled;
import com.github.mkram17.bazaarutils.utils.ScreenConstrained;
import com.github.mkram17.bazaarutils.utils.bazaar.gui.BazaarScreenMatcher;
import com.github.mkram17.bazaarutils.utils.bazaar.gui.BazaarScreenType;
import com.github.mkram17.bazaarutils.utils.annotations.modules.Module;
import com.github.mkram17.bazaarutils.utils.bazaar.market.order.OrderInfo;
import com.github.mkram17.bazaarutils.utils.ToggleableFeature;
import com.github.mkram17.bazaarutils.utils.minecraft.SlotHighlight;
import com.github.mkram17.bazaarutils.utils.minecraft.gui.ScreenMatcher;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import tech.thatgravyboat.skyblockapi.api.events.base.Subscription;
import tech.thatgravyboat.skyblockapi.api.events.base.predicates.OnlyOnSkyBlock;
import tech.thatgravyboat.skyblockapi.api.events.screen.ScreenInitializedEvent;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Module
public class InstantSellHighlight extends BUListener implements SlotHighlight, ToggleableFeature, ScreenConstrained {
    public static final Identifier IDENTIFIER = Identifier.tryBuild(BazaarUtils.MOD_ID, "highlights/standard_background");

    @Override
    public Identifier getIdentifier() {
        return IDENTIFIER;
    }

    private static final Map<Integer, Integer> colorCache = new ConcurrentHashMap<>();

    private void populateCache(Set<String> names, AbstractContainerScreen<ChestMenu> screen, Inventory playerInventory) {
        colorCache.clear();

        for (Slot slot : screen.getMenu().slots) {
            if (!slot.hasItem() || slot.container != playerInventory) continue;

            Component customName = slot.getItem().getCustomName();

            if (customName == null) continue;

            String itemName = customName.getString();

            if (names.stream().anyMatch(itemName::equalsIgnoreCase)) {
                colorCache.put(slot.getContainerSlot(), InventoryConfig.INSTANT_SELL_HIGHLIGHT_COLOR);
            }
        }
    }

    @Override
    public Integer getHighlightColor(int slotIndex) {
        return colorCache.get(slotIndex);
    }

    private static final ScreenMatcher<BazaarScreenType> SCREENS = BazaarScreenMatcher.of(BazaarScreenType.MAIN_PAGE, BazaarScreenType.SEARCH_PAGE, BazaarScreenType.PRODUCTS_CATALOG_PAGE, BazaarScreenType.PRODUCT_PAGE);

    @Override
    public ScreenMatcher<BazaarScreenType> screenConstraints() {
        return SCREENS;
    }

    @Override
    public boolean isEnabled() {
        return InventoryConfig.INSTANT_SELL_HIGHLIGHT_TOGGLE;
    }

    public InstantSellHighlight() {
        super();
    }

    @Subscription
    @OnlyWhenEnabled
    @OnlyOnSkyBlock
    @OnlyBazaarScreen(useConstraintsInterface = true)
    private void onContainerLoaded(ContainerLoadedEvent event) {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null) return;

        List<OrderInfo> orders = SellableAPI.InstantSell.orders();
        if (orders.isEmpty()) return;

        Set<String> names = orders.stream().map(OrderInfo::getName).collect(Collectors.toSet());
        populateCache(names, event.getScreen(), client.player.getInventory());
    }

    @Subscription
    private void onScreenInitialized(ScreenInitializedEvent event) {
        colorCache.clear();
    }
}