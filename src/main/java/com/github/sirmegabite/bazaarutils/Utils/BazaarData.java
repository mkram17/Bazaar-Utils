package com.github.sirmegabite.bazaarutils.Utils;

import com.github.sirmegabite.bazaarutils.configs.BUConfig;
import com.google.gson.*;

import java.io.FileReader;
import java.io.IOException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import static com.github.sirmegabite.bazaarutils.configs.BUConfig.watchedItems;

public class BazaarData {

    private static String jsonString;
    private static final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    private static final String productNameFile = "C:\\mc modding\\Bazaar-Utils\\src\\main\\resources\\Bazaar Resources.json";
    static ScheduledExecutorService bzExecutor = Executors.newScheduledThreadPool(5);


    public static<T> String getAsPrettyJsonObject(T object){
        return gson.toJson(object);
    }
    public static JsonObject getAsJsonObjectFromString(String str){
        return JsonParser.parseString(str).getAsJsonObject();
    }

    //called in init
    public static void scheduleBazaar(){
        bzExecutor.scheduleAtFixedRate(() -> {
            if(BUConfig.modEnabled) {
                APIUtils.API.getSkyBlockBazaar().whenComplete((reply, throwable) -> {
                    if (throwable != null) {
                        Util.notifyAll("Exception thrown trying to get bazaar data");
                        throwable.printStackTrace();
                    } else {
                        jsonString = getAsPrettyJsonObject(reply);
                        if (!watchedItems.isEmpty()) {
                            ItemData.update();
                        } else {
                            Util.notifyAll("no items in watchedItems");
                        }
                    }
                });
            }
        }, 1, 2, TimeUnit.SECONDS);
    }

    //(product id, what you are looking for in quick status-- either buyPrice or sellPrice)
    public static Double findItemPrice(String productId, ItemData.priceTypes priceType) {
        double sellPrice = -1;
        double buyPrice = -1;
        try {
            JsonObject products = getAsJsonObjectFromString(jsonString).get("products").getAsJsonObject();
            JsonObject item = products.get(productId).getAsJsonObject();
            JsonArray buy_summary = item.get("buy_summary").getAsJsonArray();
            JsonArray sell_summary = item.get("sell_summary").getAsJsonArray();
            //sell summary is buy orders, and buy summary is sell orders
            sellPrice =  sell_summary.get(0).getAsJsonObject().get("pricePerUnit").getAsDouble();
            buyPrice = buy_summary.get(0).getAsJsonObject().get("pricePerUnit").getAsDouble();
//            System.out.println("Buy/sell price of: "+  lookingFor + " " + buyPrice + "/" + sellPrice);
        } catch (Exception e) {
            Util.notifyAll("There was an error fetching Json objects (probably caused by incorrect product ID [" + productId + "]): ");
            e.printStackTrace();
        }

        if (priceType == ItemData.priceTypes.INSTASELL) {
//            Util.notifyAll("Price found: " + sellPrice);
            return sellPrice;
        } else if (priceType == ItemData.priceTypes.INSTABUY) {
//            Util.notifyAll("Price found: " +buyPrice);
            return buyPrice;
        }
        return null;
    }

    //returns null if it cant find anything, gets product id from natural name
    public static String findProductId(String name){
        JsonObject resources;
        JsonObject bazaarConversions;

        try (FileReader reader = new FileReader(productNameFile)){
            resources = gson.fromJson(reader, JsonObject.class);
            bazaarConversions = resources.getAsJsonObject("bazaarConversions");

            for (String key : bazaarConversions.keySet()) {
                if (bazaarConversions.get(key).getAsString().equalsIgnoreCase(name)) {
                    return key;
                }
            }

        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return null;
    }


}