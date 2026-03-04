package com.github.mkram17.bazaarutils.features.gui.buttons.bookmarks;

import com.github.mkram17.bazaarutils.utils.bazaar.data.BazaarDataManager;
import com.github.mkram17.bazaarutils.utils.bazaar.market.price.MarketPrices;
import lombok.Getter;
import net.minecraft.item.ItemStack;

@Getter
public class Bookmark {
    private final String name;
    private final ItemStack itemStack;
    private final MarketPrices marketPrices;
    private final String productID;

    public Bookmark(String name, ItemStack itemStack, MarketPrices marketPrices) {
        this.name = name;
        this.itemStack = itemStack;
        this.marketPrices = marketPrices;
        this.productID = BazaarDataManager.findProductIdOptional(name).orElseThrow();
    }
}