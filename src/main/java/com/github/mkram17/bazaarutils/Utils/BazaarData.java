package com.github.mkram17.bazaarutils.Utils;

import com.github.mkram17.bazaarutils.BazaarUtils;
import com.google.gson.*;

import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import static com.github.mkram17.bazaarutils.configs.BUConfig.watchedItems;

public class BazaarData {

    private static String jsonString;
    private static final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    private static final String productNameFile = "C:\\mc modding\\Bazaar-Utils\\src\\main\\resources\\Bazaar Resources.json";
    private static final String dataFile = "C:\\mc modding\\Bazaar-Utils\\src\\main\\resources\\Bazaar Json.json";
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
            if(BazaarUtils.config.enabled) {
                APIUtils.API.getSkyBlockBazaar().whenComplete((reply, throwable) -> {
                    if (throwable != null) {
                        Util.notifyAll("Exception thrown trying to get bazaar data");
                        throwable.printStackTrace();
                    } else {
                        jsonString = getAsPrettyJsonObject(reply);
                        writeJsonToFile(jsonString);

                        if (!watchedItems.isEmpty()) {
                            ItemData.update();
                        }
                    }
                });
            }
        }, 1, 2, TimeUnit.SECONDS);
    }

    private static void writeJsonToFile(String jsonString) {
        try (FileWriter writer = new FileWriter(dataFile)) {
            writer.write(jsonString);
        } catch (IOException e) {
            Util.notifyAll("Error writing JSON data to file: " + e.getMessage(), Util.notificationTypes.BAZAARDATA);
            e.printStackTrace();
        }
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
        } catch (Exception e) {
            Util.notifyAll("There was an error fetching Json objects (probably caused by incorrect product ID [" + productId + "]): ", Util.notificationTypes.BAZAARDATA);
            e.printStackTrace();
        }

        if (priceType == ItemData.priceTypes.INSTASELL) {
            Util.notifyAll("Price found: " + sellPrice, Util.notificationTypes.BAZAARDATA);
            return sellPrice;
        } else if (priceType == ItemData.priceTypes.INSTABUY) {
            Util.notifyAll("Price found: " +buyPrice, Util.notificationTypes.BAZAARDATA);
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
        Util.notifyAll("Couldnt find product id", Util.notificationTypes.BAZAARDATA);
        return null;
    }


}