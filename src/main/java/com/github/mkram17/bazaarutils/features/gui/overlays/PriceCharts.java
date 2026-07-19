package com.github.mkram17.bazaarutils.features.gui.overlays;

import com.github.mkram17.bazaarutils.config.features.gui.OverlaysConfig;
import com.github.mkram17.bazaarutils.events.predicates.OnlyWhenEnabled;
import com.github.mkram17.bazaarutils.utils.annotations.modules.Module;
import com.github.mkram17.bazaarutils.events.BUListener;
import com.github.mkram17.bazaarutils.utils.bazaar.data.BazaarDataUtil;
import com.github.mkram17.bazaarutils.utils.ToggleableFeature;
import com.github.mkram17.bazaarutils.utils.bazaar.gui.BazaarScreenType;
import com.github.mkram17.bazaarutils.utils.bazaar.market.order.OrderInfo;
import com.github.mkram17.bazaarutils.utils.minecraft.gui.ScreenManager;
import com.github.mkram17.bazaarutils.utils.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ConfirmLinkScreen;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;
import tech.thatgravyboat.skyblockapi.api.events.base.Subscription;
import tech.thatgravyboat.skyblockapi.api.events.base.predicates.OnlyOnSkyBlock;
import tech.thatgravyboat.skyblockapi.api.events.screen.ItemTooltipEvent;
import tech.thatgravyboat.skyblockapi.api.events.screen.SlotClickEvent;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Module
public class PriceCharts extends BUListener implements ToggleableFeature {
    // Cache: sanitized item name -> should show tooltip
    private static final Map<String, Boolean> SHOW_CACHE = new ConcurrentHashMap<>();

    @Override
    public boolean isEnabled() {
        return OverlaysConfig.PRICE_CHARTS_TOGGLE;
    }

    public PriceCharts() {}

    @Subscription
    @OnlyWhenEnabled
    @OnlyOnSkyBlock
    public void onTooltip(ItemTooltipEvent event) {
        var stack = event.getItem();
        var lines = event.getTooltip();

        if (stack.isEmpty() || !shouldShow()) return;
        if (stack.getItem().getName().getString().contains("GLASS_PANE")) return;

        String key = sanitizeName(stack.getHoverName().getString());

        // Lazily populate cache if a synced/replaced stack appears later
        if (!SHOW_CACHE.computeIfAbsent(key, OrderInfo::isValidName)) {
            return;
        }

        MutableComponent text = Component.literal("CTRL+SHIFT click for price charts & other info")
                .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD);
        MutableComponent poweredBy = Component.literal("Powered by skyblock.finance")
                .withStyle(ChatFormatting.GRAY);

        lines.add(Component.literal(""));
        lines.add(text);
        lines.add(poweredBy);
    }

    @Subscription
    @OnlyWhenEnabled
    @OnlyOnSkyBlock
    private void onClick(SlotClickEvent event) {
        if (!shouldShow() || event.isCancelled()) {
            return;
        }

        if (!Minecraft.getInstance().hasShiftDown() || !Minecraft.getInstance().hasControlDown()) {
            return;
        }

        String itemName = sanitizeName(event.getSlot().getItem().getHoverName().getString());

        if (!SHOW_CACHE.getOrDefault(itemName, false)) {
            return;
        }

        String productID = BazaarDataUtil.findProductIdOptional(itemName).get(); // All cached items are safe
        String link = "https://skyblock.finance/items/" + productID;

        Minecraft.getInstance().setScreen(new ConfirmLinkScreen(confirmed -> {
            if (confirmed) {
                try {
                    net.minecraft.util.Util.getPlatform().openUri(new URI(link));
                } catch (URISyntaxException ex) {
                    Util.notifyError("Failed to open skyblock.finance link.", ex);
                }
            }
            Minecraft.getInstance().setScreen(null);
        }, link, true));


        event.cancel();
    }

    private boolean shouldShow() {
        return (OverlaysConfig.PRICE_CHARTS_SHOW_OUTSIDE_BAZAAR || ScreenManager.getInstance().isCurrent(BazaarScreenType.values()))
                && !ScreenManager.getInstance().isCurrent(BazaarScreenType.MAIN_PAGE);
    }

    private static String sanitizeName(String raw){
        int len = raw.length();

        if (len > 3 && raw.charAt(len - 2) == 'x' && Character.isDigit(raw.charAt(len - 1))) {
            int idx = raw.lastIndexOf('x');
            if (idx > 0) return raw.substring(0, idx - 1);
        }

        return raw;
    }
}
