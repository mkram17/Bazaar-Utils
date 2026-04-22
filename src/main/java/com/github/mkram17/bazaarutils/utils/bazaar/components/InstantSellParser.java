package com.github.mkram17.bazaarutils.utils.bazaar.components;

import com.github.mkram17.bazaarutils.misc.NotificationType;
import com.github.mkram17.bazaarutils.utils.BazaarLogger;
import com.github.mkram17.bazaarutils.utils.PlayerLogger;
import com.github.mkram17.bazaarutils.utils.Util;
import com.github.mkram17.bazaarutils.utils.bazaar.market.order.OrderInfo;
import com.github.mkram17.bazaarutils.utils.bazaar.market.TransactionType;
import com.github.mkram17.bazaarutils.utils.minecraft.components.LoreParser;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

public final class InstantSellParser {
    private static final BazaarLogger LOG = BazaarLogger.of(InstantSellParser.class);

    public record InstantSellResult(List<OrderInfo> items, Optional<OtherItems> otherItems) {
        public record OtherItems(int volume, double totalValue) {}
    }

    private InstantSellParser() {}

    public static InstantSellResult parseOrders(ItemStack instantSellStack) {
        List<OrderInfo> items = new ArrayList<>();
        Optional<InstantSellResult.OtherItems> otherItems = Optional.empty();

        for (Component line : LoreParser.lines(instantSellStack)) {
            List<Component> siblings = line.getSiblings();
            if (siblings.size() != 6) continue;

            String name = siblings.get(3).getString().trim();

            try {
                int volume = Util.parseNumber(siblings.get(1).getString());
                double totalPrice = Double.parseDouble(siblings.get(5).getString().replace(" coins", "").replace(",", ""));
                double pricePerUnit = Math.round(totalPrice / volume * 10) / 10.0;

                if (name.equals("Other items")) {
                    otherItems = Optional.of(new InstantSellResult.OtherItems(volume, totalPrice));
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

        PlayerLogger.debug("InstantSell (overview page) parsed: %d known items | %d folded to \"Other Items\"".formatted(items.size(), otherItems.map(InstantSellResult.OtherItems::volume).orElse(0)), NotificationType.SCREEN_PARSING);

        return new InstantSellResult(List.copyOf(items), otherItems);
    }

    public static Optional<InstantSellResult> parseItemPageOrder(ItemStack sellInstantlyStack) {
        List<Component> lines = LoreParser.lines(sellInstantlyStack);
        if (lines.size() < 6) return Optional.empty();

        try {
            String name = lines.get(0).getSiblings().getFirst().getString().trim();

            List<Component> inventoryLineSiblings = lines.get(2).getSiblings();
            if (inventoryLineSiblings.size() < 2 || inventoryLineSiblings.get(1).getString().contains("None")) {
                LOG.info("parseItemPageOrder: no inventory for '{}' — skipping", name);

                return Optional.empty();
            }

            int volume = Util.parseNumber(lines.get(4).getSiblings().get(1).getString());
            double totalPrice = Double.parseDouble(lines.get(5).getSiblings().get(1).getString().replace(" coins", "").replace(",", ""));
            double pricePerUnit = Math.round(totalPrice / volume * 10) / 10.0;

            Optional<OrderInfo> result = OrderInfo.of(name, TransactionType.Side.BUY, pricePerUnit, volume);

            if (result.isEmpty()) {
                PlayerLogger.send("Could not resolve '%s' — try /bu updateresources or restart the game.".formatted(name));

                return Optional.empty();
            }

            PlayerLogger.debug("InstantSell (item page) parsed: %s %dx@%.4f".formatted(result.get().getName(), result.get().getVolume(), result.get().getPricePerItem()), NotificationType.SCREEN_PARSING);

            return Optional.of(new InstantSellResult(List.of(result.get()), Optional.empty()));
        } catch (Exception e) {
            LOG.warn("parseItemPageOrder: failed to parse lore — stack='{}'", sellInstantlyStack.getDisplayName().getString(), e);

            return Optional.empty();
        }
    }
}