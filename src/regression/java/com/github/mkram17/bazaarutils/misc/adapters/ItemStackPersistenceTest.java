package com.github.mkram17.bazaarutils.misc.adapters;

import com.github.mkram17.bazaarutils.features.Bookmark;
import com.github.mkram17.bazaarutils.misc.orderinfo.ItemInfo;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import net.minecraft.SharedConstants;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.Items;

/** Standalone regression check; no Minecraft client or server connection is required. */
public class ItemStackPersistenceTest {
    private static final String TUNGSTEN_JSON = """
            {"id":"minecraft:paper","count":1,"components":{
              "minecraft:item_model":"hypixel_skyblock:item/glacite/tungsten/tungsten",
              "minecraft:custom_name":{"text":"","extra":[{"text":"SELL ","color":"gold","bold":true},{"text":"Tungsten","color":"white"}],"italic":false},
              "minecraft:lore":[{"text":"Worth 412.5k coins","color":"dark_gray","italic":false},"",{"text":"","extra":[{"text":"Offer amount: ","color":"gray"},{"text":"1,792","color":"green"},{"text":"x","color":"gray"}],"italic":false},"",{"text":"","extra":[{"text":"Price per unit: ","color":"gray"},{"text":"232.5 coins","color":"gold"}],"italic":false},"",{"text":"Click to view options!","color":"yellow","italic":false}],
              "minecraft:custom_data":{"id":"TUNGSTEN"}
            }}
            """;

    public static void main(String[] args) {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        check(!Items.PAPER.builtInRegistryHolder().areComponentsBound(), "Test must start before item components are bound");

        JsonElement reportedItem = JsonParser.parseString(TUNGSTEN_JSON);
        var originalFailure = ItemStack.CODEC.parse(JsonOps.INSTANCE, reportedItem).error();
        check(originalFailure.isPresent()
                        && originalFailure.get().message().contains("does not have components yet"),
                "The reported failure must be reproduced by the old codec");

        Gson gson = new GsonBuilder()
                .registerTypeAdapter(ItemStackTemplate.class, new ItemStackTemplateGsonAdapter())
                .create();
        ItemStackTemplate template = gson.fromJson(reportedItem, ItemStackTemplate.class);
        check(template != null && template.item().value() == Items.PAPER, "Reported item must load as a paper template");
        check(template.count() == 1, "Item count must survive loading");
        check(template.equals(gson.fromJson(gson.toJson(template), ItemStackTemplate.class)),
                "Name, lore, model and custom data must survive saving before joining a world");
        check(gson.fromJson("null", ItemStackTemplate.class) == null, "Null item snapshots must remain absent");

        JsonObject orderJson = new JsonObject();
        orderJson.addProperty("slotIndex", 5);
        orderJson.add("itemStack", reportedItem);
        ItemInfo order = gson.fromJson(orderJson, ItemInfo.class);
        check(order.slotIndex() == 5 && template.equals(order.itemTemplate()), "Existing order JSON must load without migration");
        JsonObject savedOrder = gson.toJsonTree(order).getAsJsonObject();
        check(savedOrder.has("itemStack") && !savedOrder.has("itemTemplate"), "Order JSON field names must stay compatible");
        check(order.equals(gson.fromJson(savedOrder, ItemInfo.class)), "Order snapshots must survive a restart");

        JsonObject bookmarkJson = new JsonObject();
        bookmarkJson.addProperty("name", "Tungsten");
        bookmarkJson.add("bookmarkedItemStack", reportedItem);
        Bookmark bookmark = gson.fromJson(bookmarkJson, Bookmark.class);
        JsonObject savedBookmark = gson.toJsonTree(bookmark).getAsJsonObject();
        check(template.equals(gson.fromJson(savedBookmark.get("bookmarkedItemStack"), ItemStackTemplate.class)),
                "Existing bookmark JSON must retain its item components before joining a world");
        Bookmark reloadedBookmark = gson.fromJson(savedBookmark, Bookmark.class);
        check(!Items.PAPER.builtInRegistryHolder().areComponentsBound(), "Loading and saving must not bind or access item defaults");

        // Simulate the component binding performed when joining a world.
        Items.PAPER.builtInRegistryHolder().bindComponents(DataComponents.COMMON_ITEM_COMPONENTS);
        ItemStack stack = order.itemStack();
        check(!stack.isEmpty() && stack.getHoverName().getString().equals("SELL Tungsten"), "Order stack must resolve after joining");
        check(stack.get(DataComponents.LORE).lines().size() == 7, "Reported lore must survive restoration");
        check(stack.get(DataComponents.CUSTOM_DATA).copyTag().getString("id").orElseThrow().equals("TUNGSTEN"),
                "SkyBlock item ID must survive restoration");
        check(ItemStack.matches(stack, reloadedBookmark.getBookmarkedItemStack()), "Bookmark stack must resolve after a restart");
        check(template.equals(new ItemInfo(5, stack).itemTemplate()), "Live order items must save as compatible templates");
        bookmark.setBookmarkedItemStack(stack);
        check(ItemStack.matches(stack, bookmark.getBookmarkedItemStack()), "Live bookmark items must save as compatible templates");
        bookmark.setBookmarkedItemStack(ItemStack.EMPTY);
        check(bookmark.getBookmarkedItemStack() == null, "Empty bookmark snapshots must remain absent");
        check(new ItemInfo(5, ItemStack.EMPTY).itemStack().isEmpty(), "Empty order snapshots must remain empty");
        check(gson.fromJson("{\"slotIndex\":5,\"itemStack\":null}", ItemInfo.class).itemStack().isEmpty(),
                "Previously absent order items must remain safe");
        System.out.println("Item persistence regression checks passed.");
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
