package com.github.mkram17.bazaarutils.data.bazaar.book;

import java.util.List;

public record BookLevels(List<PriceLevel> asksLevels, List<PriceLevel> bidLevels) {}
