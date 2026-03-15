package com.github.mkram17.bazaarutils.utils.codecs;

import com.github.mkram17.bazaarutils.utils.bazaar.market.price.MarketPrices;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;

import java.lang.reflect.Type;

/**
 * Serializes MarketPrices to a stable shape and reconstructs instances through the constructor.
 */
public class MarketPricesGsonAdapter implements JsonSerializer<MarketPrices>, JsonDeserializer<MarketPrices> {
    private static final String PRODUCT_ID_FIELD = "productID";

    @Override
    public JsonElement serialize(MarketPrices src, Type typeOfSrc, JsonSerializationContext context) {
        JsonObject object = new JsonObject();
        object.addProperty(PRODUCT_ID_FIELD, src.getProductID());

        return object;
    }

    @Override
    public MarketPrices deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
        JsonObject object = json.getAsJsonObject();

        if (!object.has(PRODUCT_ID_FIELD) || object.get(PRODUCT_ID_FIELD).isJsonNull()) {
            throw new JsonParseException("Missing productID for MarketPrices");
        }

        return new MarketPrices(object.get(PRODUCT_ID_FIELD).getAsString());
    }
}

