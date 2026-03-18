package com.github.mkram17.bazaarutils.utils.bazaar.market.price;

import com.github.mkram17.bazaarutils.utils.bazaar.market.order.Order;
import com.github.mkram17.bazaarutils.utils.bazaar.market.order.OrderInfo;
import com.github.mkram17.bazaarutils.utils.bazaar.market.order.TransactionType2;
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
    protected TransactionType2 transactionType;

    @Setter @Getter
    protected PricingPosition pricingPosition;

    @Setter @Getter
    protected Double pricePerItem;


    public PriceInfo(Double pricePerItem, TransactionType2 transactionType) {
        this.transactionType = transactionType;

        if (pricePerItem != null) {
            //TODO figure out best rounding. Eg to the tenth, hundredth or thousandth
            this.pricePerItem = (double) Math.round(pricePerItem * 10) / 10;
        }
        if (transactionType == null) {
            // if the transactionType is null, its value does not matter, but the rest of the code expects one.
            //TODO revisit whether this still needs to have default value
            this.transactionType = TransactionType2.of(TransactionType2.Side.SELL, TransactionType2.Method.ORDER);
        }
    }
}
