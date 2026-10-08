package com.github.mkram17.bazaarutils.features.gui.inventory.restrictions.controls;

import com.github.mkram17.bazaarutils.features.gui.inventory.restrictions.RestrictionTarget;
import com.github.mkram17.bazaarutils.testsupport.MarketDataFixture;
import com.github.mkram17.bazaarutils.testsupport.MinecraftTestSupport;
import com.github.mkram17.bazaarutils.utils.bazaar.market.order.OrderInfo;
import com.github.mkram17.bazaarutils.utils.bazaar.market.order.TransactionType;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.*;

class RestrictionControlTest {
    @BeforeAll
    static void bootstrap() {
        MinecraftTestSupport.bootstrap();
    }

    @ParameterizedTest
    @CsvSource({
            "PRICE, 99, true", "PRICE, 100, false", "PRICE, 101, false",
            "VOLUME, 9, true", "VOLUME, 10, false", "VOLUME, 11, false"
    })
    void numericRulesUseTotalValueAndExcludeEquality(NumericRestrictBy rule, double threshold, boolean expected) {
        try (var market = new MarketDataFixture()) {
            var item = new OrderInfo("Mithril", TransactionType.Side.BUY, null, 10, 10.0, null);
            assertEquals(expected, new DoubleRestrictionControl(rule, threshold).shouldRestrict(item));
        }
    }

    @ParameterizedTest
    @CsvSource({"enchanted mithril, true", "MITHRIL, false", "Enchanted, false", "Enchanted Mithril Ore, false"})
    void nameRulesRequireTheFullNameIgnoringCase(String ruleName, boolean expected) {
        try (var market = new MarketDataFixture()) {
            var item = new OrderInfo("Enchanted Mithril", TransactionType.Side.BUY, null, 10, 10.0, null);
            assertEquals(expected, new StringRestrictionControl(ruleName).shouldRestrict(item));
        }
    }

    @Test
    void numericAndNameRulesOnlyApplyToConfiguredTargets() {
        var numeric = new DoubleRestrictionControl(NumericRestrictBy.PRICE, 100);
        var named = new StringRestrictionControl("Mithril");
        for (RestrictionControl<?> control : new RestrictionControl<?>[]{numeric, named}) {
            assertTrue(control.appliesTo(RestrictionTarget.INSTANT_SELL));
            assertTrue(control.appliesTo(RestrictionTarget.SELL_SACKS));
        }
        numeric.setTargets(new RestrictionTarget[]{RestrictionTarget.INSTANT_SELL});
        named.setTargets(new RestrictionTarget[]{RestrictionTarget.SELL_SACKS});
        assertTrue(numeric.appliesTo(RestrictionTarget.INSTANT_SELL));
        assertFalse(numeric.appliesTo(RestrictionTarget.SELL_SACKS));
        assertTrue(named.appliesTo(RestrictionTarget.SELL_SACKS));
        assertFalse(named.appliesTo(RestrictionTarget.INSTANT_SELL));
    }
}
