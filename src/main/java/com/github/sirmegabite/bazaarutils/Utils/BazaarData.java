package com.github.sirmegabite.bazaarutils.Utils;

import com.github.sirmegabite.bazaarutils.BazaarUtils;
import com.google.gson.*;

import java.io.FileReader;
import java.io.IOException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import static com.github.sirmegabite.bazaarutils.BazaarUtils.watchedItems;

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
            if(BazaarUtils.modEnabled) {
                APIUtils.API.getSkyBlockBazaar().whenComplete((reply, throwable) -> {
                    if (throwable != null) {
                        Util.notifyAll("Exception thrown trying to get bazaar data" , BazaarData.class);
                        throwable.printStackTrace();
                    } else {
                        jsonString = getAsPrettyJsonObject(reply);
//                    Util.sendFirst(jsonString, 100, "Bazaar future returned: ");
//                        FMLLog.info("Bazaar future returned without thrown exception");
                        if (!watchedItems.isEmpty()) {
                            ItemData.update();
                        } else {
                            Util.notifyConsole("no items in watchedItems", BazaarData.class);
                        }
                    }
                });
            }
        }, 1, 2, TimeUnit.SECONDS);
    }

    //(product id, what you are looking for in quick status-- either buyPrice or sellPrice)
    public static Double findItemPrice(String prod, String lookingFor) {

        JsonElement sellPrice = null;
        JsonElement buyPrice = null;
//        System.out.println("finding price of: "+  lookingFor);
        try {
            JsonObject products = getAsJsonObjectFromString(jsonString).get("products").getAsJsonObject();
            JsonObject item = products.get(prod).getAsJsonObject();
            JsonArray buy_summary = item.get("buy_summary").getAsJsonArray();
            JsonArray sell_summary = item.get("sell_summary").getAsJsonArray();
            //sell summary is buy orders, and buy summary is sell orders
            sellPrice =  sell_summary.get(0).getAsJsonObject().get("pricePerUnit");
            buyPrice = buy_summary.get(0).getAsJsonObject().get("pricePerUnit");
//            System.out.println("Buy/sell price of: "+  lookingFor + " " + buyPrice + "/" + sellPrice);
        } catch (Exception e) {
            System.out.println("There was an error fetching Json objects (probably caused by incorrect item name): ");
            e.printStackTrace();
        }

        /* condition ? expressionIfTrue : expressionIfFalse; */
        if ("sellPrice".equals(lookingFor)) {
            assert sellPrice != null;
            return sellPrice.getAsDouble();
        } else if ("buyPrice".equals(lookingFor)) {
            assert buyPrice != null;
            return buyPrice.getAsDouble();
        }
        //code executed after this point will only run if cant find lookingFor
        Util.notifyAll("Could not find lookingFor: " + lookingFor, BazaarData.class);
        //could not find what lookingFor is, so return null
        return null;
    }



    public static String getJsonString(){
        return jsonString;
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