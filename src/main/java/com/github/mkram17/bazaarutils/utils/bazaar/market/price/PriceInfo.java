package com.github.mkram17.bazaarutils.utils.bazaar.market.price;

import com.github.mkram17.bazaarutils.utils.bazaar.market.order.Order;
import com.github.mkram17.bazaarutils.utils.bazaar.market.order.OrderInfo;
import com.github.mkram17.bazaarutils.utils.bazaar.market.order.TransactionType;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

/**
 * Container for market price metadata of a single product. For actual user orders, prefer
 * {@link OrderInfo} or {@link Order}.
 */
@ToString
public class PriceInfo {
    @Setter @Getter
    protected TransactionType transactionType;

    @Setter @Getter
    protected PricingPosition pricingPosition;

    @Setter @Getter
    protected Double pricePerItem;


    /**
     * Creates market price metadata for a product snapshot.
     *
     * <p>{@code pricePerItem} is rounded to one decimal place when present. If
     * {@code transactionType} is {@code null}, a default sell-order type is assigned so downstream
     * logic can rely on a non-null value.</p>
     */
    public PriceInfo(Double pricePerItem, TransactionType transactionType) {
        this.transactionType = transactionType;

        if (pricePerItem != null) {
            //TODO figure out best rounding. Eg to the tenth, hundredth or thousandth
            this.pricePerItem = (double) Math.round(pricePerItem * 10) / 10;
        }
        if (transactionType == null) {
            // if the transactionType is null, its value does not matter, but the rest of the code expects one.
            //TODO revisit whether this still needs to have default value
            this.transactionType = TransactionType.SELL_ORDER;
        }
    }
}
