package com.github.mkram17.bazaarutils.testsupport;

import com.github.mkram17.bazaarutils.utils.bazaar.data.BazaarDataUtil;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalInt;

/** Keeps real order constructors independent of the live API and resource repository. */
public final class MarketDataFixture implements AutoCloseable {
    private final MockedStatic<BazaarDataUtil> data = Mockito.mockStatic(BazaarDataUtil.class);

    public MarketDataFixture() {
        data.when(() -> BazaarDataUtil.findProductIdOptional(Mockito.anyString()))
                .thenReturn(Optional.of("TEST_PRODUCT"));
        data.when(() -> BazaarDataUtil.isValidProductId(Mockito.anyString())).thenReturn(true);
        data.when(() -> BazaarDataUtil.findItemPriceOptional(Mockito.anyString(), Mockito.any()))
                .thenReturn(OptionalDouble.of(10.0));
        data.when(() -> BazaarDataUtil.getOrderCountOptional(Mockito.anyString(), Mockito.any(), Mockito.anyDouble()))
                .thenReturn(OptionalInt.of(1));
    }

    @Override
    public void close() {
        data.close();
    }
}
