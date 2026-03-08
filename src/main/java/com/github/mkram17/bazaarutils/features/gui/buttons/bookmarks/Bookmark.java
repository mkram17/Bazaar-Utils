package com.github.mkram17.bazaarutils.features.gui.buttons.bookmarks;

import com.github.mkram17.bazaarutils.utils.bazaar.market.price.MarketPrices;
import net.minecraft.item.ItemStack;

public record Bookmark(String name, ItemStack itemStack, MarketPrices marketPrices) {}