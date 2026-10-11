package com.github.mkram17.bazaarutils.utils.bazaar.market.order;

import com.github.mkram17.bazaarutils.testsupport.MinecraftTestSupport;
import com.github.mkram17.bazaarutils.utils.Util;
import com.github.mkram17.bazaarutils.utils.bazaar.data.BazaarDataUtil;
import com.github.mkram17.bazaarutils.utils.bazaar.market.price.PricingPosition;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mockito;

import java.util.OptionalDouble;

import static org.junit.jupiter.api.Assertions.*;

class OrderUtilTest {
    private static final String PRODUCT = "MITHRIL";

    @BeforeAll
    static void bootstrap() {
        MinecraftTestSupport.bootstrap();
    }

    @ParameterizedTest
    @CsvSource({
            "BUY, COMPETITIVE, 10.1", "BUY, MATCHED, 10.0", "BUY, OUTBID, 9.9",
            "SELL, COMPETITIVE, 19.9", "SELL, MATCHED, 20.0", "SELL, OUTBID, 20.1"
    })
    void priceHelpersUseTheCorrectMarketSideAndOffset(TransactionType.Side side, PricingPosition position, double expected) {
        try (var data = Mockito.mockStatic(BazaarDataUtil.class)) {
            data.when(() -> BazaarDataUtil.findItemPriceOptional(Mockito.eq(PRODUCT), Mockito.any()))
                    .thenAnswer(invocation -> {
                        TransactionType type = invocation.getArgument(1);
                        return OptionalDouble.of(type.getSide() == TransactionType.Side.BUY ? 10.0 : 20.0);
                    });
            var type = TransactionType.of(side, TransactionType.Method.ORDER);
            assertEquals(expected, OrderUtil.getPriceForPositionOptional(PRODUCT, position, type).orElseThrow(), 1e-9);
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"BUY", "SELL", "BOTH"})
    void unavailableMarketDataCannotProduceAnActionablePrice(String missingSide) {
        try (var data = Mockito.mockStatic(BazaarDataUtil.class);
             var notifications = Mockito.mockStatic(Util.class)) {
            data.when(() -> BazaarDataUtil.findItemPriceOptional(Mockito.eq(PRODUCT), Mockito.any()))
                    .thenAnswer(invocation -> {
                        TransactionType type = invocation.getArgument(1);
                        return missingSide.equals("BOTH") || missingSide.equals(type.getSide().name())
                                ? OptionalDouble.empty() : OptionalDouble.of(10.0);
                    });
            for (var side : TransactionType.Side.values()) {
                assertTrue(OrderUtil.getPriceForPositionOptional(PRODUCT, PricingPosition.COMPETITIVE,
                        TransactionType.of(side, TransactionType.Method.ORDER)).isEmpty());
            }
        }
    }
}
