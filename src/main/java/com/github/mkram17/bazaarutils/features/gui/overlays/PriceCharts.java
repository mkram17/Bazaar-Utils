package com.github.mkram17.bazaarutils.features.gui.overlays;

import com.github.mkram17.bazaarutils.config.features.gui.InventoryConfig;
import com.github.mkram17.bazaarutils.utils.*;
import com.github.mkram17.bazaarutils.utils.annotations.modules.ItemModifier;
import com.github.mkram17.bazaarutils.utils.bazaar.market.ProductInfo;
import com.github.mkram17.bazaarutils.utils.bazaar.gui.BazaarScreenMatcher;
import com.github.mkram17.bazaarutils.utils.bazaar.gui.BazaarScreenType;
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

import java.net.URI;
import java.net.URISyntaxException;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;

@ItemModifier
public class PriceCharts implements LoreModifier, AbstractItemModifier {
    private static final BazaarLogger LOG = BazaarLogger.of(PriceCharts.class);

    @Override
    public boolean isEnabled() {
        return InventoryConfig.PRICE_CHARTS_TOGGLE;
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
        return InventoryConfig.PRICE_CHARTS_SHOW_OUTSIDE_BAZAAR || LoreModifier.super.appliesToScreen(context);
    }

    public final EnumSet<ModifierSource> MODIFIER_SOURCES = EnumSet.of(ModifierSource.CONTAINER, ModifierSource.PLAYER_INVENTORY, ModifierSource.HOTBAR);

    @Override
    public EnumSet<ModifierSource> getModifierSources() {
        return MODIFIER_SOURCES; // to prevent instantiating the list very single iteration
    }

    public PriceCharts() {}

    @Override
    public boolean appliesTo(ItemStack stack) {
        return ProductInfo.fromItemStack(stack).isPresent();
    }

    @Override
    public Result modifyLore(ItemStack stack, List<Component> lore, @Nullable Result previous, @Nullable ScreenContext context) {
        Optional<ProductInfo> product = ProductInfo.fromItemStack(stack);
        if (product.isEmpty()) return Result.UNMODIFIED;

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

        Optional<ProductInfo> productInfo = ProductInfo.fromItemStack(stack);
        if (productInfo.isEmpty()) {
            PlayerLogger.sendError("Could not resolve '%s' — try /bu updateresources or restart the game.".formatted(stack.getDisplayName().toString()), new Throwable());

            return Result.UNMODIFIED;
        }

        String link = "https://skyblock.finance/items/" + productInfo.get().getProductId();

        Minecraft.getInstance().setScreen(new ConfirmLinkScreen(confirmed -> {
            if (confirmed) {
                try {
                    net.minecraft.util.Util.getPlatform().openUri(new URI(link));
                } catch (URISyntaxException exception) {
                    PlayerLogger.sendError("Failed to open skyblock.finance link", exception);
                }
            }

            Minecraft.getInstance().setScreen(null);
        }, link, true));

        return Result.CONSUMED;
    }
}
