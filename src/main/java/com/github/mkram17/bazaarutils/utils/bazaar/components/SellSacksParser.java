package com.github.mkram17.bazaarutils.utils.bazaar.components;

import com.github.mkram17.bazaarutils.misc.NotificationType;
import com.github.mkram17.bazaarutils.utils.BazaarLogger;
import com.github.mkram17.bazaarutils.utils.PlayerLogger;
import com.github.mkram17.bazaarutils.utils.Util;
import com.github.mkram17.bazaarutils.utils.bazaar.market.order.OrderInfo;
import com.github.mkram17.bazaarutils.utils.bazaar.market.TransactionType;
import com.github.mkram17.bazaarutils.utils.minecraft.components.LoreParser;
import net.minecraft.world.item.ItemStack;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

public final class SellSacksParser {
    private static final BazaarLogger LOG = BazaarLogger.of(SellSacksParser.class);

    public record SellSacksResult(List<OrderInfo> items, Optional<OtherItems> otherItems) {
        public record OtherItems(int volume, double totalValue) {}
    }

    private SellSacksParser() {}

    public static SellSacksResult parseOrders(ItemStack sellSacksStack) {
        List<OrderInfo> items = new ArrayList<>();
        Optional<SellSacksResult.OtherItems> otherItems = Optional.empty();

        for (Component line : LoreParser.lines(sellSacksStack)) {
            List<Component> siblings = line.getSiblings();
            if (siblings.size() != 6) continue;

            String name = siblings.get(3).getString().trim();

            try {
                int volume = Util.parseNumber(siblings.get(1).getString());
                double totalPrice = Double.parseDouble(siblings.get(5).getString().replace(" coins", "").replace(",", ""));
                double pricePerUnit = Math.round(totalPrice / volume * 10) / 10.0;

                if (name.equals("Other items")) {
                    otherItems = Optional.of(new SellSacksResult.OtherItems(volume, totalPrice));
                } else {
                    Optional<OrderInfo> result = OrderInfo.of(name, TransactionType.Side.BUY, pricePerUnit, volume);

                    if (result.isEmpty()) {
                        PlayerLogger.send("Could not resolve '%s' — try /bu updateresources or restart the game.".formatted(name));

                        continue;
                    }

                    items.add(result.get());
                }
            } catch (Exception e) {
                LOG.warn("parseOrders: failed to parse lore line — siblings=[{}]", siblings.stream().map(Component::getString).collect(Collectors.joining(", ")), e);
            }
        }

        PlayerLogger.debug("SellSacks parsed: %d known items | %d folded to \"Other Items\"".formatted(items.size(), otherItems.map(SellSacksResult.OtherItems::volume).orElse(0)), NotificationType.SCREEN_PARSING);

        return new SellSacksResult(List.copyOf(items), otherItems);
    }
}