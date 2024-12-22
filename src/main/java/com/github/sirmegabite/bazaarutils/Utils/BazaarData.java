package com.github.sirmegabite.bazaarutils.Utils;

import com.github.sirmegabite.bazaarutils.BazaarUtils;
import com.google.gson.*;

import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import static com.github.sirmegabite.bazaarutils.BazaarUtils.watchedItems;

public class BazaarData {

    private static String jsonString;
    private static final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    private static final String bazaarDataFile = "C:\\mc modding\\AutoBazaar\\src\\main\\resources\\Bazaar Data";
    private static final String productNameFile = "C:\\mc modding\\Bazaar-Utils\\src\\main\\resources\\Bazaar product names.json";
    ScheduledExecutorService bzExecutor = Executors.newScheduledThreadPool(5);

    public static<T> String getAsPrettyJsonObject(T object){
        return gson.toJson(object);
    }
    public static JsonObject getAsJsonObjectFromString(String str){
        return JsonParser.parseString(str).getAsJsonObject();
    }

    //called in init
    public void scheduleBazaar(){
        bzExecutor.scheduleAtFixedRate(() -> {
            if(BazaarUtils.modEnabled) {
                APIUtils.API.getSkyBlockBazaar().whenComplete((reply, throwable) -> {
                    if (throwable != null) {
                        Util.notifyAll("Exception thrown trying to get bazaar data" , this.getClass());
                        throwable.printStackTrace();
                    } else {
                        jsonString = getAsPrettyJsonObject(reply);
//                    Util.sendFirst(jsonString, 100, "Bazaar future returned: ");
//                        FMLLog.info("Bazaar future returned without thrown exception");
                        writeFile();
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
    public static String getProductIdFromName(String name){
        String productId = null;
        JsonArray bazaarNames;

        try (FileReader reader = new FileReader(productNameFile)){
            bazaarNames = gson.fromJson(reader, JsonArray.class);

            for (int i = 0; i < bazaarNames.size(); i++) {
                JsonObject obj = bazaarNames.get(i).getAsJsonObject();

                if (obj.get("bazaar_product_name").getAsString().equalsIgnoreCase(name)) {
                    productId = obj.get("bazaar_product_id").getAsString();
                    System.out.println("Bazaar product found: " + productId + " from: " + name);
                    break;
                }
            }

        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return productId;
    }

    //writes json file of bazaar data

    private static void writeFile() {
        //assumes that jsonString is already set with getBazaarJson future
//        System.out.println("Writing data to file...");
        try{
            Files.write(Paths.get(bazaarDataFile), jsonString.getBytes());
//            Util.notifyConsole("Data written to file successfully.", BazaarData.class);
//            Util.sendFirst(jsonString, 500, "Written to file: ");
        }catch (Exception e){
            System.out.println("Failed to write data to file");
            e.printStackTrace();
        }
    }

}