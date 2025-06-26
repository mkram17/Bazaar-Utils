package com.github.mkram17.bazaarutils.data;

import com.github.mkram17.bazaarutils.BazaarUtils;
import com.github.mkram17.bazaarutils.config.BUConfig;
import com.github.mkram17.bazaarutils.events.BUListener;
import com.github.mkram17.bazaarutils.misc.orderinfo.OrderData;
import com.github.mkram17.bazaarutils.misc.orderinfo.OrderPriceInfo;
import com.github.mkram17.bazaarutils.utils.Util;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.hypixel.api.reply.skyblock.SkyBlockBazaarReply;
import net.minecraft.client.MinecraftClient;
import net.minecraft.resource.Resource;
import net.minecraft.resource.ResourceManager;
import net.minecraft.util.Identifier;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

//TODO more efficient timing of api requests
public class BazaarData implements BUListener {
    private static final String PRODUCT_NAME_RESOURCE = "bazaar-resources.json";
    static ScheduledExecutorService bzExecutor = Executors.newScheduledThreadPool(5);
    private static SkyBlockBazaarReply bazaarReply = null;
    private static int bazaarDataPeriod = 1;
    private static int exceptionCount = 0;
    private static int bazaarCalls = 0;
    private static boolean skipNextCall = false;
    private static final long bazaarDataDelay = 3L;

    @Override
    public void subscribe(){
        scheduleBazaar();
    }

    public static void scheduleBazaar() {

        bzExecutor.scheduleAtFixedRate(() -> {
            if (!(bazaarCalls % bazaarDataPeriod == 0))
                return;
            if (skipNextCall) {
                skipNextCall = false;
                return;
            }

            APIUtils.getSkyBlockBazaarWithRetry().whenComplete((reply, throwable) -> {
                bazaarCalls++;
                if (bazaarCalls % 10 == 0 || bazaarCalls < 5)
                    skipNextCall = true;

                if (throwable != null) {
                    skipNextCall = true;
                    exceptionCount++;
                    Util.notifyError("Exception thrown trying to get bazaar data", throwable);
                    System.out.println("[Bazaar Utils] Error info: period-" + bazaarDataPeriod + ", exceptionCount-" + exceptionCount);
                    System.out.println("[Bazaar Utils] Status: " + APIUtils.API.getStatus(APIUtils.uuid));
                    if (exceptionCount % 5 == 0) {
                        bazaarDataPeriod++;
                    }
                } else {
                    if (reply == null) {
                        Util.notifyError("Bazaar data is null", null);
                        return;
                    }
                    bazaarReply = reply;
//                        writeJsonToFile(jsonString);

                    if (!BUConfig.get().watchedOrders.isEmpty()) {
                        OrderData.updateOutdatedItems();
                    }
                }
            });
        }, bazaarDataDelay, 1, TimeUnit.SECONDS);
    }

    public static int getOrderCount(String productId, OrderPriceInfo.priceTypes priceType, double price) {
        if (bazaarReply == null) {
            Util.notifyError("Bazaar data is null", null);
            return -1;
        }
        try {
            SkyBlockBazaarReply.Product product = bazaarReply.getProduct(productId);
            if (product == null) {
                Util.notifyError("Could not find item using product ID: " + productId, null);
                return -1;
            }

            java.util.List<SkyBlockBazaarReply.Product.Summary> summaryList;
            if (priceType == OrderPriceInfo.priceTypes.INSTABUY) {
                summaryList = product.getBuySummary();
            } else if (priceType == OrderPriceInfo.priceTypes.INSTASELL) {
                summaryList = product.getSellSummary();
            } else {
                return -1; // invalid price type
            }

            int numOrders =0;

            for( SkyBlockBazaarReply.Product.Summary summary : summaryList) {
                if (summary.getPricePerUnit() == price) {
                    return (int) summary.getOrders();
                }
            }

            return numOrders;
        } catch (Exception e) {
            Util.notifyError("There was an error fetching order count (probably caused by incorrect product ID [" + productId + "])", e);
            return -1;
        }
    }

    public static JsonObject loadResourceJson(String resourcePath) {
        ResourceManager resourceManager = MinecraftClient.getInstance().getResourceManager();
        Identifier resourceId = Identifier.of(BazaarUtils.MODID, resourcePath);

        try {
            Optional<Resource> optionalResource = resourceManager.getResource(resourceId);
            if (optionalResource.isPresent()) {
                Resource resource = optionalResource.get();
                try (InputStream inputStream = resource.getInputStream();
                     InputStreamReader reader = new InputStreamReader(inputStream)) {
                    return JsonParser.parseReader(reader).getAsJsonObject();
                }
            } else {
                Util.notifyError("Could not find resource: " + resourcePath, null);
                return new JsonObject();
            }
        } catch (IOException e) {
            Util.notifyError("Error reading resource: " + resourcePath, e);
            return new JsonObject();
        }
    }

    public static Double findItemPrice(String productId, OrderPriceInfo.priceTypes priceType) {
        if (bazaarReply == null) {
            Util.notifyError("Bazaar data is null", null);
            return -1.0;
        }
        try {
            SkyBlockBazaarReply.Product product = bazaarReply.getProduct(productId);
            if (product == null) {
                Util.notifyError("Could not find item using product ID: " + productId, null);
                return -1.0;
            }

            var sell_order_summary = product.getBuySummary();
            var buy_order_summary = product.getSellSummary();

            if (priceType == OrderPriceInfo.priceTypes.INSTABUY) {
                if (sell_order_summary.isEmpty()) {
                    Util.notifyAll("Buy summary is empty for product ID: " + productId, Util.notificationTypes.BAZAARDATA);
                    return 0.0;
                }
                double sellOrderPrice = sell_order_summary.getFirst().getPricePerUnit();
                return sellOrderPrice;
            } else if (priceType == OrderPriceInfo.priceTypes.INSTASELL) {
                if (buy_order_summary.isEmpty()) {
                    Util.notifyAll("Sell summary is empty for product ID: " + productId + ", returning 0 for INSTABUY.", Util.notificationTypes.BAZAARDATA);
                    return 0.0;
                }
                double buyOrderPrice = buy_order_summary.getFirst().getPricePerUnit();
                return buyOrderPrice;
            }
        } catch (Exception e) {
            Util.notifyError("There was an error fetching product data (probably caused by incorrect product ID [" + productId + "])", e);
            return -1.0;
        }
        // Should not be reached if priceType is INSTASELL or INSTABUY
        return null;
    }

    //returns null if it cant find anything, gets product id from natural name
    public static String findProductId(String name) {
        JsonObject resources;
        JsonObject bazaarConversions;

        try {
            resources = loadResourceJson(PRODUCT_NAME_RESOURCE);
            bazaarConversions = resources.getAsJsonObject("bazaarConversions");

            for (String key : bazaarConversions.keySet()) {
                if (bazaarConversions.get(key).getAsString().equalsIgnoreCase(name)) {
                    return key;
                }
            }
        } catch (Exception e) {
            Util.logMessage("Error while finding product ID from name: " + name + "." + e.getStackTrace());
        }

//        Util.notifyAll("Couldn't find product id", Util.notificationTypes.BAZAARDATA);
        return null;
    }

}
