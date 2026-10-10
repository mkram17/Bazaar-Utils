package com.github.mkram17.bazaarutils.utils.bazaar.components;

import com.github.mkram17.bazaarutils.testsupport.MarketDataFixture;
import com.github.mkram17.bazaarutils.testsupport.MinecraftTestSupport;
import com.github.mkram17.bazaarutils.utils.PlayerActionUtil;
import com.github.mkram17.bazaarutils.utils.bazaar.market.order.OrderInfo;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mockito;

import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

class SellLoreParserTest {
    private enum Parser { INSTANT_SELL, SELL_SACKS }
    private record OtherItems(int volume, double totalValue) {}
    private record Parsed(List<OrderInfo> items, Optional<OtherItems> otherItems) {}

    @BeforeAll
    static void bootstrap() {
        MinecraftTestSupport.bootstrap();
    }

    @ParameterizedTest
    @EnumSource(Parser.class)
    void parsesQuantitiesTotalsAndRoundingWhileSeparatingOtherItems(Parser parser) {
        try (var market = new MarketDataFixture()) {
            var parsed = parse(parser, stack(
                    "Items to sell:",
                    " 1,234x Enchanted Mithril for 12,345.6 coins",
                    " 300x Other items for 2,345.7 coins",
                    "Click to sell!"));
            assertEquals(1, parsed.items().size());
            var item = parsed.items().getFirst();
            assertEquals("Enchanted Mithril", item.getName());
            assertEquals(1234, item.getVolume());
            assertEquals(10.0, item.getPricePerItem(), 1e-9);
            assertEquals(new OtherItems(300, 2345.7), parsed.otherItems().orElseThrow());
        }
    }

    @ParameterizedTest
    @EnumSource(Parser.class)
    void missingLoreReturnsAnEmptyResult(Parser parser) {
        var parsed = parse(parser, stack());
        assertTrue(parsed.items().isEmpty());
        assertTrue(parsed.otherItems().isEmpty());
    }

    @ParameterizedTest
    @EnumSource(Parser.class)
    void malformedAndZeroQuantityLinesAreSkippedWithoutDroppingValidItems(Parser parser) {
        try (var market = new MarketDataFixture()) {
            var parsed = parse(parser, stack(
                    "The server changed this line",
                    " 5x Broken Item for 1.2.3 coins",
                    " 0x Mithril for 10 coins",
                    " 0x Other items for 0 coins",
                    " 3x Fig Log for 10.1 coins"));
            assertEquals(1, parsed.items().size());
            assertEquals("Fig Log", parsed.items().getFirst().getName());
            assertEquals(3, parsed.items().getFirst().getVolume());
            assertEquals(3.4, parsed.items().getFirst().getPricePerItem(), 1e-9);
            assertTrue(parsed.otherItems().isEmpty());
        }
    }

    @Test
    void productPageParsesCommaSeparatedAmountAndDecimalTotal() {
        try (var market = new MarketDataFixture();
             var notifications = Mockito.mockStatic(PlayerActionUtil.class)) {
            var result = InstantSellParser.parseProductPageOrder(productStack("Amount: 1,234x", "Total: 12,345.6 coins"))
                    .orElseThrow();
            assertEquals(1, result.items().size());
            var item = result.items().getFirst();
            assertEquals("Enchanted Mithril", item.getName());
            assertEquals(1234, item.getVolume());
            assertEquals(10.0, item.getPricePerItem(), 1e-9);
            assertTrue(result.otherItems().isEmpty());
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"Amount: 0x", "Amount: unknown", "There are no Buy Orders!", "Inventory: None"})
    void productPageWithNoSellableQuantityOrInvalidAmountReturnsEmpty(String amountLine) {
        try (var market = new MarketDataFixture();
             var notifications = Mockito.mockStatic(PlayerActionUtil.class)) {
            assertTrue(InstantSellParser.parseProductPageOrder(productStack(amountLine, "Total: 10 coins")).isEmpty());
        }
    }

    private static Parsed parse(Parser parser, ItemStack stack) {
        if (parser == Parser.INSTANT_SELL) {
            var result = InstantSellParser.parseInstantSellOrders(stack);
            return new Parsed(result.items(), result.otherItems().map(other -> new OtherItems(other.volume(), other.totalValue())));
        }
        var result = SellSacksParser.parseSackOrders(stack);
        return new Parsed(result.items(), result.otherItems().map(other -> new OtherItems(other.volume(), other.totalValue())));
    }

    private static ItemStack stack(String... lines) {
        return stackWithLore(Stream.of(lines).map(Component::literal).map(Component.class::cast).toList());
    }

    private static ItemStack productStack(String amountLine, String totalLine) {
        return stackWithLore(List.of(
                Component.literal("Selling: ").append(Component.literal("Enchanted Mithril")),
                Component.literal(amountLine), Component.literal(totalLine)));
    }

    private static ItemStack stackWithLore(List<Component> lines) {
        // Item defaults are loaded from data packs in 26.2; only the supplied lore matters here.
        var components = DataComponentMap.builder();
        if (!lines.isEmpty()) components.set(DataComponents.LORE, new ItemLore(lines));
        var stack = Mockito.mock(ItemStack.class);
        Mockito.when(stack.getComponents()).thenReturn(components.build());
        return stack;
    }
}
