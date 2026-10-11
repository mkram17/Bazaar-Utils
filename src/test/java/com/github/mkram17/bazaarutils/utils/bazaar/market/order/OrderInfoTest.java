package com.github.mkram17.bazaarutils.utils.bazaar.market.order;

import com.github.mkram17.bazaarutils.testsupport.MarketDataFixture;
import com.github.mkram17.bazaarutils.testsupport.MinecraftTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class OrderInfoTest {
    private MarketDataFixture market;

    @BeforeAll
    static void bootstrap() {
        MinecraftTestSupport.bootstrap();
    }

    @BeforeEach
    void setUp() {
        market = new MarketDataFixture();
    }

    @AfterEach
    void tearDown() {
        market.close();
    }

    @Test
    void strictMatchesTakePrecedenceEvenWhenALooseMatchHasACloserPrice() {
        var query = info(100, 10.0);
        var loose = order(99, 10.0);
        var strict = order(100, 10.1);
        assertEquals(List.of(strict), query.findAllMatchesInList(List.of(loose, strict)));
        assertSame(strict, query.findOrderInList(List.of(loose, strict)).orElseThrow());
    }

    @ParameterizedTest
    @CsvSource({"94, false", "95, true", "105, true", "106, false"})
    void looseMatchingIncludesTheFivePercentVolumeBoundary(int observedVolume, boolean expected) {
        var query = info(observedVolume, 10.0);
        var stored = order(100, 10.0);
        assertEquals(expected, query.isSimilarTo(stored, false));
        assertFalse(query.isSimilarTo(stored, true));
    }

    @Test
    void looseMatchingCanUseTheUnclaimedQuantity() {
        var stored = order(100, 10.0);
        stored.setAmountFilled(80);
        stored.setAmountClaimed(30);
        var query = info(50, 10.0);
        assertFalse(query.isSimilarTo(stored, true));
        assertTrue(query.isSimilarTo(stored, false));
        assertSame(stored, query.findOrderInList(List.of(stored)).orElseThrow());
    }

    @Test
    void ambiguousMatchesChooseClosestVolumeBeforeClosestPrice() {
        var query = info(100, 10.0);
        var closerPrice = order(103, 10.0);
        var closerVolume = order(99, 10.1);
        assertSame(closerVolume, query.findOrderInList(List.of(closerPrice, closerVolume)).orElseThrow());
    }

    @Test
    void equalVolumesChooseTheClosestPrice() {
        var query = info(100, 10.0);
        var farther = order(100, 10.1);
        var closer = order(100, 10.0);
        assertSame(closer, query.findOrderInList(List.of(farther, closer)).orElseThrow());
    }

    @Test
    void matchingIgnoresNameCaseButRejectsOtherNamesAndSides() {
        var query = info(100, 10.0);
        var wrongName = new Order("Enchanted Mithril", 100, 10.0, TransactionType.Side.BUY, null);
        var wrongSide = new Order("Mithril", 100, 10.0, TransactionType.Side.SELL, null);
        var sameName = new Order("MITHRIL", 100, 10.0, TransactionType.Side.BUY, null);
        assertTrue(query.findOrderInList(List.of(wrongName, wrongSide)).isEmpty());
        assertEquals(List.of(sameName), query.findAllMatchesInList(List.of(wrongName, wrongSide, sameName)));
    }

    private static OrderInfo info(int volume, double price) {
        return new OrderInfo("Mithril", TransactionType.Side.BUY, null, volume, price, null);
    }

    private static Order order(int volume, double price) {
        return new Order("Mithril", volume, price, TransactionType.Side.BUY, null);
    }
}
