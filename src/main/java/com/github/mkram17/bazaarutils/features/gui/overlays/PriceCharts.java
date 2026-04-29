package com.github.mkram17.bazaarutils.features.gui.overlays;

import com.github.mkram17.bazaarutils.config.features.gui.OverlaysConfig;
import com.github.mkram17.bazaarutils.utils.*;
import com.github.mkram17.bazaarutils.utils.annotations.modules.ItemModifier;
import com.github.mkram17.bazaarutils.utils.bazaar.data.BazaarDataUtil;
import com.github.mkram17.bazaarutils.utils.bazaar.gui.BazaarScreenMatcher;
import com.github.mkram17.bazaarutils.utils.bazaar.gui.BazaarScreenType;
import com.github.mkram17.bazaarutils.utils.bazaar.market.order.OrderInfo;
import com.github.mkram17.bazaarutils.utils.minecraft.gui.ScreenContext;
import com.github.mkram17.bazaarutils.utils.minecraft.gui.ScreenMatcher;
import com.github.mkram17.bazaarutils.utils.minecraft.item.modifier.AbstractItemModifier;
import com.github.mkram17.bazaarutils.utils.minecraft.item.modifier.LoreModifier;
import com.github.mkram17.bazaarutils.utils.minecraft.item.modifier.ModifyIndicator;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ConfirmLinkScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;
import tech.thatgravyboat.skyblockapi.api.datatype.DataTypeItemStackKt;
import tech.thatgravyboat.skyblockapi.api.datatype.DataTypes;
import tech.thatgravyboat.skyblockapi.impl.tagkey.ItemTag;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@ItemModifier
public class PriceCharts implements LoreModifier, AbstractItemModifier {
    // Cache: sanitized item name -> should show tooltip
    private static final Map<String, Boolean> SHOW_CACHE = new ConcurrentHashMap<>();

    @Override
    public boolean isEnabled() {
        return OverlaysConfig.PRICE_CHARTS_TOGGLE;
    }

    @Override
    public ModifyIndicator.IndicatorPlacement indicatorPlacement() {
        return ModifyIndicator.IndicatorPlacement.AT_MODIFICATION;
    }

    public final ScreenMatcher<BazaarScreenType> SCREENS = BazaarScreenMatcher.any();

    @Override
    public ScreenMatcher<BazaarScreenType> screenConstrains() {
        return SCREENS;
    }

    @Override
    public boolean appliesToScreen(@Nullable ScreenContext context) {
        return OverlaysConfig.PRICE_CHARTS_SHOW_OUTSIDE_BAZAAR || LoreModifier.super.appliesToScreen(context);
    }

    public final List<ModifierSource> MODIFIER_SOURCES = List.of(ModifierSource.CONTAINER, ModifierSource.PLAYER_INVENTORY, ModifierSource.HOTBAR);

    @Override
    public List<ModifierSource> getModifierSources() {
        return MODIFIER_SOURCES; // to prevent instantiating the list very single iteration
    }

    public PriceCharts() {}

    @Override
    public boolean appliesTo(ItemStack stack) {
        if (stack.isEmpty() || ItemTag.GLASS_PANES.contains(stack)) return false;

        String key = DataTypeItemStackKt.getData(stack, DataTypes.INSTANCE.getCLEAN_NAME());

        return SHOW_CACHE.computeIfAbsent(key, OrderInfo::isValidName);
    }

    @Override
    public Result modifyLore(ItemStack stack, List<Component> lore, @Nullable Result previous, @Nullable ScreenContext context) {
        String key = DataTypeItemStackKt.getData(stack, DataTypes.INSTANCE.getCLEAN_NAME());

        if (!SHOW_CACHE.computeIfAbsent(key, OrderInfo::isValidName)) return Result.UNMODIFIED;

        return withMerger(lore, merger -> {
            copyAll(merger);

            space(merger);

            merger.add(Component.literal("CTRL+SHIFT click for price charts & other info")
                    .withStyle(style -> style
                            .withColor(ChatFormatting.GOLD)
                            .withBold(true)
                            .withItalic(false)));
            merger.add(withAtModificationIndicator(
                    Component.literal("Powered by skyblock.finance")
                            .withStyle(style -> style
                                    .withColor(ChatFormatting.GRAY)
                                    .withBold(false)
                                    .withItalic(false))));

            return Result.HANDLED;
        });
    }

    @Override
    public Result onClick(ItemStack stack, int button, @Nullable Slot slot, @Nullable ScreenContext context) {
        if (!Minecraft.getInstance().hasShiftDown() || !Minecraft.getInstance().hasControlDown()) return Result.UNMODIFIED;

        String key = DataTypeItemStackKt.getData(stack, DataTypes.INSTANCE.getCLEAN_NAME());

        if (!SHOW_CACHE.getOrDefault(key, false)) return Result.UNMODIFIED;

        Optional<String> productId = BazaarDataUtil.findProductIdOptional(key); // All cached items are safe
        if (productId.isEmpty()) return Result.UNMODIFIED;

        String link = "https://skyblock.finance/items/" + productId.get();

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

        return Result.CONSUMED;
    }
}
