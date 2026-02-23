package com.github.mkram17.bazaarutils.features.gui.overlays;

import com.github.mkram17.bazaarutils.config.features.gui.OverlaysConfig;
import com.github.mkram17.bazaarutils.utils.annotations.modules.Module;
import com.github.mkram17.bazaarutils.utils.bazaar.data.BazaarDataManager;
import com.github.mkram17.bazaarutils.events.SlotClickEvent;
import com.github.mkram17.bazaarutils.events.listener.BUListener;
import com.github.mkram17.bazaarutils.features.util.BUToggleableFeature;
import com.github.mkram17.bazaarutils.utils.bazaar.market.order.OrderInfo;
import com.github.mkram17.bazaarutils.utils.ScreenInfo;
import com.github.mkram17.bazaarutils.utils.Util;
import com.teamresourceful.resourcefulconfig.api.annotations.Comment;
import com.teamresourceful.resourcefulconfig.api.annotations.ConfigEntry;
import com.teamresourceful.resourcefulconfig.api.annotations.ConfigObject;
import lombok.Getter;
import meteordevelopment.orbit.EventHandler;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ConfirmLinkScreen;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Module
public class PriceCharts extends BUListener implements ItemTooltipCallback, BUToggleableFeature {
    // Cache: sanitized item name -> should show tooltip
    private static final Map<String, Boolean> SHOW_CACHE = new ConcurrentHashMap<>();

    public boolean isEnabled() {
        return OverlaysConfig.PRICE_CHARTS_TOGGLE;
    }

    public PriceCharts() {}

    @Override
    public void getTooltip(ItemStack stack, Item.TooltipContext ctx, TooltipType type, List<Text> lines) {
        if (!isEnabled() || stack == null || stack.isEmpty() || !shouldShow()) return;
        if (stack.getItem().getName().getString().contains("GLASS_PANE")) return;

        String key = sanitizeName(stack.getName().getString());

        // Lazily populate cache if a synced/replaced stack appears later
        if (!SHOW_CACHE.computeIfAbsent(key, OrderInfo::isValidName)) {
            return;
        }

        MutableText text = Text.literal("CTRL+SHIFT click for price charts & other info")
                .formatted(Formatting.GOLD, Formatting.BOLD);
        MutableText poweredBy = Text.literal("Powered by skyblock.finance")
                .formatted(Formatting.GRAY);

        lines.add(Text.literal(""));
        lines.add(text);
        lines.add(poweredBy);
    }

    @EventHandler
    private void onClick(SlotClickEvent e){
        if (!isEnabled() || !shouldShow() || e.isCancelled()) {
            return;
        }

        if (!MinecraftClient.getInstance().isShiftPressed() || !MinecraftClient.getInstance().isCtrlPressed()) {
            return;
        }

        String itemName = sanitizeName(e.slot.getStack().getName().getString());

        if (!SHOW_CACHE.getOrDefault(itemName, false)) {
            return;
        }

        String productID = BazaarDataManager.findProductIdOptional(itemName).get(); // All cached items are safe
        String link = "https://skyblock.finance/items/" + productID;

        MinecraftClient.getInstance().setScreen(new ConfirmLinkScreen(confirmed -> {
            if (confirmed) {
                try {
                    net.minecraft.util.Util.getOperatingSystem().open(new URI(link));
                } catch (URISyntaxException ex) {
                    Util.notifyError("Failed to open skyblock.finance link.", ex);
                }
            }
            MinecraftClient.getInstance().setScreen(null);
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
        ScreenInfo screenInfo = ScreenInfo.getCurrentScreenInfo();

        return (screenInfo.inBazaar() || OverlaysConfig.PRICE_CHARTS_SHOW_OUTSIDE_BAZAAR) && !screenInfo.inMenu(ScreenInfo.BazaarMenuType.BAZAAR_MAIN_PAGE);
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
