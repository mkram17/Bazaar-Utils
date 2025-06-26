package com.github.mkram17.bazaarutils.features;

import com.github.mkram17.bazaarutils.config.BUConfig;
import com.github.mkram17.bazaarutils.events.BUListener;
import com.github.mkram17.bazaarutils.events.ChestLoadedEvent;
import com.github.mkram17.bazaarutils.misc.orderinfo.OrderPriceInfo;
import com.github.mkram17.bazaarutils.utils.GUIUtils;
import com.github.mkram17.bazaarutils.utils.Util;
import dev.isxander.yacl3.api.Option;
import dev.isxander.yacl3.api.OptionDescription;
import lombok.Getter;
import lombok.Setter;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.LoreComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.github.mkram17.bazaarutils.BazaarUtils.eventBus;

public class OrderPriceCapture implements BUListener {
    
    @Getter @Setter
    private boolean enabled = true;
    
    private static final Map<String, ConfirmationData> confirmationCache = new HashMap<>();
    
    private static final Map<String, Long> recentlyProcessed = new HashMap<>();
    private static final long DUPLICATE_PREVENTION_WINDOW = 5000; // 5 seconds
    
    private static final Pattern CONFIRM_BUY_PATTERN = Pattern.compile("Confirm Buy Order");
    private static final Pattern CONFIRM_SELL_PATTERN = Pattern.compile("Confirm Sell Offer");
    
    private static final Pattern PRICE_PER_UNIT_PATTERN = Pattern.compile("Price per unit: ([0-9,]+(?:\\.[0-9]+)?) coins");
    
    private static final Pattern ORDER_PATTERN = Pattern.compile("(?:Order: |Selling: )([0-9,]+)x (.+)");
    
    public static class ConfirmationData {
        public final String itemName;
        public final int volume;
        public final double pricePerUnit;
        public final OrderPriceInfo.priceTypes orderType;
        public final long timestamp;
        
        public ConfirmationData(String itemName, int volume, double pricePerUnit, OrderPriceInfo.priceTypes orderType) {
            this.itemName = itemName;
            this.volume = volume;
            this.pricePerUnit = pricePerUnit;
            this.orderType = orderType;
            this.timestamp = System.currentTimeMillis();
        }
        
        public String getCacheKey() {
            return itemName.toLowerCase() + "_" + volume + "_" + orderType.name();
        }
        
        public boolean isFresh() {
            return (System.currentTimeMillis() - timestamp) < 10000; // 10 second timeout
        }
    }
    
    public OrderPriceCapture(boolean enabled) {
        this.enabled = enabled;
    }
    
    public void onChestLoaded(ChestLoadedEvent e) {
        if (!enabled) return;
        
        String containerName = GUIUtils.getContainerName();
        if (containerName == null) return;
        
        boolean isConfirmBuy = CONFIRM_BUY_PATTERN.matcher(containerName).find();
        boolean isConfirmSell = CONFIRM_SELL_PATTERN.matcher(containerName).find();
        
        if (!isConfirmBuy && !isConfirmSell) return;
        
        ItemStack confirmationItem = findConfirmationItem(e);
        if (confirmationItem == null) return;
        
        try {
            ConfirmationData data = extractConfirmationData(confirmationItem, isConfirmSell);
            if (data != null) {
                String confirmationKey = data.getCacheKey() + "_" + System.currentTimeMillis() / 1000; // Group by second
                
                Long lastProcessed = recentlyProcessed.get(data.getCacheKey());
                if (lastProcessed != null && (System.currentTimeMillis() - lastProcessed) < DUPLICATE_PREVENTION_WINDOW) {
                    return; // Skip duplicate
                }
                
                recentlyProcessed.put(data.getCacheKey(), System.currentTimeMillis());
                
                confirmationCache.put(data.getCacheKey(), data);
                
                cleanupCache();
                cleanupRecentlyProcessed();
                
                Util.notifyAll(String.format("Captured %s order: %dx %s at %.1f coins per unit", 
                              data.orderType.getString(), data.volume, data.itemName, data.pricePerUnit), 
                              Util.notificationTypes.ITEMDATA);
            }
        } catch (Exception ex) {
            Util.notifyError("Error capturing order confirmation data", ex);
        }
    }
    
    private ConfirmationData extractConfirmationData(ItemStack confirmationItem, boolean isSellOrder) {
        LoreComponent lore = confirmationItem.getComponents().get(DataComponentTypes.LORE);
        if (lore == null) return null;
        
        String itemName = null;
        int quantity = 0;
        double pricePerUnit = 0;
        
        // Parse the lore to extract relevant information
        for (Text line : lore.lines()) {
            String lineText = line.getString();
            
            // Extract price per unit
            Matcher priceMatcher = PRICE_PER_UNIT_PATTERN.matcher(lineText);
            if (priceMatcher.find()) {
                String priceString = priceMatcher.group(1).replace(",", "");
                pricePerUnit = Double.parseDouble(priceString);
            }
            
            // Extract item name and quantity
            Matcher orderMatcher = ORDER_PATTERN.matcher(lineText);
            if (orderMatcher.find()) {
                quantity = Integer.parseInt(orderMatcher.group(1).replace(",", ""));
                itemName = orderMatcher.group(2);
            }
        }
        
        if (itemName == null || pricePerUnit == 0 || quantity == 0) {
            return null;
        }
        
        OrderPriceInfo.priceTypes orderType = isSellOrder ? 
            OrderPriceInfo.priceTypes.INSTABUY : OrderPriceInfo.priceTypes.INSTASELL;
        
        return new ConfirmationData(itemName, quantity, pricePerUnit, orderType);
    }
    
    private ItemStack findConfirmationItem(ChestLoadedEvent e) {
        for (ItemStack stack : e.getItemStacks()) {
            if (stack == null || stack.isEmpty()) continue;
            
            LoreComponent lore = stack.getComponents().get(DataComponentTypes.LORE);
            if (lore == null) continue;
            
            for (Text line : lore.lines()) {
                if (PRICE_PER_UNIT_PATTERN.matcher(line.getString()).find()) {
                    return stack;
                }
            }
        }
        return null;
    }
    
    public static ConfirmationData getCachedConfirmation(String itemName, int volume, OrderPriceInfo.priceTypes orderType) {
        String key = itemName.toLowerCase() + "_" + volume + "_" + orderType.name();
        ConfirmationData data = confirmationCache.get(key);
        
        if (data != null && data.isFresh()) {
            confirmationCache.remove(key); // Remove after use
            return data;
        }
        
        return null;
    }
    
    private void cleanupCache() {
        confirmationCache.entrySet().removeIf(entry -> !entry.getValue().isFresh());
    }
    
    private void cleanupRecentlyProcessed() {
        recentlyProcessed.entrySet().removeIf(entry -> (System.currentTimeMillis() - entry.getValue()) > DUPLICATE_PREVENTION_WINDOW);
    }
    
    public void subscribe() {
        if (enabled) {
            eventBus.subscribe(this);
        }
    }
    
    public Option<Boolean> createOption() {
        return Option.<Boolean>createBuilder()
                .name(Text.literal("Order Price Capture"))
                .description(OptionDescription.of(Text.literal("Capture exact price data from confirmation screens for accurate order tracking without tax calculations.")))
                .binding(true,
                        this::isEnabled,
                        this::setEnabled)
                .controller(BUConfig::createBooleanController)
                .build();
    }
} 