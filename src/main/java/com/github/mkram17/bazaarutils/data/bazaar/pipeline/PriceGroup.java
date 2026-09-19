package com.github.mkram17.bazaarutils.data.bazaar.pipeline;

import com.github.mkram17.bazaarutils.utils.bazaar.market.PriceType;
import org.jetbrains.annotations.NotNull;

public record PriceGroup(@NotNull PriceType type, double price) {}
