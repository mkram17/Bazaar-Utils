package com.github.mkram17.bazaarutils.features.gui.overlays;

import com.github.mkram17.bazaarutils.config.features.gui.OverlaysConfig;
import com.github.mkram17.bazaarutils.events.BUListener;
import com.github.mkram17.bazaarutils.utils.annotations.events.OnlyWhenEnabled;
import com.github.mkram17.bazaarutils.utils.annotations.modules.Module;
import com.github.mkram17.bazaarutils.utils.bazaar.data.BazaarDataUtil;
import com.github.mkram17.bazaarutils.utils.bazaar.gui.BazaarScreenType;
import com.github.mkram17.bazaarutils.utils.bazaar.market.order.OrderInfo;
import com.github.mkram17.bazaarutils.utils.minecraft.gui.ScreenContext;
import com.github.mkram17.bazaarutils.utils.minecraft.gui.ScreenManager;
import com.github.mkram17.bazaarutils.utils.minecraft.gui.ScreenType;
import com.github.mkram17.bazaarutils.utils.Util;
import com.github.mkram17.bazaarutils.utils.minecraft.item.modifier.AbstractItemModifier;
import com.github.mkram17.bazaarutils.utils.minecraft.item.modifier.LoreModifier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ConfirmLinkScreen;
import net.minecraft.world.item.ItemStack;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;
import org.jetbrains.annotations.Nullable;
import tech.thatgravyboat.skyblockapi.api.datatype.DataTypeItemStackKt;
import tech.thatgravyboat.skyblockapi.api.datatype.DataTypes;
import tech.thatgravyboat.skyblockapi.api.events.base.Subscription;
import tech.thatgravyboat.skyblockapi.api.events.base.predicates.IgnoreFiller;
import tech.thatgravyboat.skyblockapi.api.events.base.predicates.OnlyOnSkyBlock;
import tech.thatgravyboat.skyblockapi.api.events.screen.SlotClickEvent;
import tech.thatgravyboat.skyblockapi.impl.tagkey.ItemTag;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Module
public class PriceCharts extends BUListener implements AbstractItemModifier, LoreModifier {
    private static final Map<String, Boolean> SHOW_CACHE = new ConcurrentHashMap<>();

    @Override
    public boolean isEnabled() {
        return OverlaysConfig.PRICE_CHARTS_TOGGLE;
    }

    @Override
    public boolean appliesToScreen(Optional<ScreenContext> context) {
        return OverlaysConfig.PRICE_CHARTS_SHOW_OUTSIDE_BAZAAR || context.map(it -> it.isAnyOf(BazaarScreenType.values())).orElse(false);
    }

    public PriceCharts() {
        super();
    }

    @Override
    public boolean appliesTo(ItemStack stack) {
        String key = DataTypeItemStackKt.getData(stack, DataTypes.INSTANCE.getCLEAN_NAME());

        return !stack.isEmpty() && !ItemTag.GLASS_PANES.contains(stack) && SHOW_CACHE.computeIfAbsent(key, OrderInfo::isValidName);
    }

    @Override
    public Result modifyLore(ItemStack stack, List<Component> lore, @Nullable Result previous) {
        String key = DataTypeItemStackKt.getData(stack, DataTypes.INSTANCE.getCLEAN_NAME());

        if (!SHOW_CACHE.computeIfAbsent(key, OrderInfo::isValidName)) return Result.UNMODIFIED;

        return withMerger(lore, merger -> {
            copyAll(merger);

            space(merger);

            merger.add(Component.literal("CTRL+SHIFT click for price charts & other info").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
            merger.add(Component.literal("Powered by skyblock.finance").withStyle(ChatFormatting.GRAY));

            return Result.MODIFIED;
        });
    }

    @Subscription
    @OnlyWhenEnabled
    @OnlyOnSkyBlock
    @IgnoreFiller
    public void onSlotClick(SlotClickEvent event) {
        if (!Minecraft.getInstance().hasShiftDown() || !Minecraft.getInstance().hasControlDown()) return;

        ItemStack stack = event.getItem();
        if (!appliesTo(stack)) return;

        String itemName = DataTypeItemStackKt.getData(stack, DataTypes.INSTANCE.getCLEAN_NAME());
        if (!SHOW_CACHE.getOrDefault(itemName, false)) return;

        Optional<String> productID = BazaarDataUtil.findProductIdOptional(itemName);

        if (productID.isEmpty()) return;

        String link = "https://skyblock.finance/items/" + productID.get();

        event.cancel();

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
    }

    @Override
    public List<ModifierSource> getModifierSources() {
        return List.of(ModifierSource.INVENTORY, ModifierSource.PLAYER_INVENTORY);
    }
}