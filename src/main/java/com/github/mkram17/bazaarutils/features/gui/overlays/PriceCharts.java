package com.github.mkram17.bazaarutils.features.gui.overlays;

import com.github.mkram17.bazaarutils.config.features.gui.OverlaysConfig;
import com.github.mkram17.bazaarutils.utils.annotations.modules.Module;
import com.github.mkram17.bazaarutils.events.SlotClickEvent;
import com.github.mkram17.bazaarutils.events.listener.BUListener;
import com.github.mkram17.bazaarutils.utils.bazaar.data.BazaarDataUtil;
import com.github.mkram17.bazaarutils.utils.config.BUToggleableFeature;
import com.github.mkram17.bazaarutils.utils.bazaar.gui.BazaarScreens;
import com.github.mkram17.bazaarutils.utils.bazaar.market.order.OrderInfo;
import com.github.mkram17.bazaarutils.utils.minecraft.gui.ScreenManager;
import com.github.mkram17.bazaarutils.utils.minecraft.gui.ScreenType;
import com.github.mkram17.bazaarutils.utils.Util;
import meteordevelopment.orbit.EventHandler;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ConfirmLinkScreen;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Module
public class PriceCharts extends BUListener implements ItemTooltipCallback, BUToggleableFeature {
    // Cache: sanitized item name -> should show tooltip
    private static final Map<String, Boolean> SHOW_CACHE = new ConcurrentHashMap<>();

    @Override
    public boolean isEnabled() {
        return OverlaysConfig.PRICE_CHARTS_TOGGLE;
    }

    public PriceCharts() {}

    @Override
    public void getTooltip(ItemStack stack, Item.TooltipContext ctx, TooltipFlag type, List<Component> lines) {
        if (!isEnabled() || stack == null || stack.isEmpty() || !shouldShow()) return;
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

    @EventHandler
    private void onClick(SlotClickEvent e){
        if (!isEnabled() || !shouldShow() || e.isCancelled()) {
            return;
        }

        if (!Minecraft.getInstance().hasShiftDown() || !Minecraft.getInstance().hasControlDown()) {
            return;
        }

        String itemName = sanitizeName(e.slot.getItem().getHoverName().getString());

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

        e.cancel();
    }

    @Override
    protected void registerFabricEvents() {
        ItemTooltipCallback.EVENT.register(this);
        // Clear cache when (re)subscribing to avoid stale bazaar state leakage
        SHOW_CACHE.clear();
    }

    private boolean shouldShow() {
        return (ScreenManager.getInstance().isCurrent(BazaarScreens.ALL.toArray(ScreenType[]::new)) || OverlaysConfig.PRICE_CHARTS_SHOW_OUTSIDE_BAZAAR) && !ScreenManager.getInstance().isCurrent(BazaarScreens.MAIN_PAGE);
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
