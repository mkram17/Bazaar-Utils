package com.github.mkram17.bazaarutils.features;

import com.github.mkram17.bazaarutils.config.BUConfig;
import com.github.mkram17.bazaarutils.events.BUListener;
import com.github.mkram17.bazaarutils.misc.orderinfo.OrderData;
import dev.isxander.yacl3.api.Option;
import dev.isxander.yacl3.api.OptionDescription;
import lombok.Getter;
import lombok.Setter;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.screen.slot.Slot;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;

import java.util.HashMap;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

//drawing done in MixinHandledScreen
public class OrderStatusHighlight implements BUListener {
    @Getter @Setter
    private boolean enabled = true;
    @Getter @Setter
    private boolean filledHighlightEnabled = true;
    private static final HashMap<Integer, OrderData> highlightedOrders = new HashMap<>();
    public static final Identifier IDENTIFIER = Identifier.tryParse("bazaarutils", "orderstatushighlight/background_test");
    public static final float BACKGROUND_TRANSPARENCY = 0.8f;
    
    private static ItemTooltipCallback currentTooltipCallback = null;
    private static boolean screenEventsRegistered = false;
    private static boolean tooltipCallbackRegistered = false;
    private static long lastTooltipActivity = 0;
    private static int tooltipCallCount = 0;
    private static boolean healthMonitorStarted = false;
    private static ScheduledExecutorService healthMonitor = null;
    
    private static final long HEALTH_CHECK_INTERVAL = 30000;
    private static final long ACTIVITY_TIMEOUT = 60000;
    private static final int MIN_EXPECTED_CALLS = 5;

    public OrderStatusHighlight(boolean enabled){
        this.enabled = enabled;
    }

    public OrderStatusHighlight(boolean enabled, boolean filledHighlightEnabled){
        this.enabled = enabled;
        this.filledHighlightEnabled = filledHighlightEnabled;
    }

    private OrderData.statuses getEffectiveStatus(OrderData orderData) {
        if (orderData == null) {
            return null;
        }
        // Check if order is filled first (priority) and if filled highlighting is enabled
        if (filledHighlightEnabled && orderData.getFillStatus() == OrderData.statuses.FILLED) {
            return OrderData.statuses.FILLED;
        }
        // Otherwise return outdated status
        return orderData.getOutdatedStatus();
    }

    public static OrderData.statuses getHighlightType(int slotIndex) {
        OrderStatusHighlight currentInstance = BUConfig.get().orderStatusHighlight;
        if (currentInstance == null) return null;
        
        return currentInstance.getEffectiveStatus(highlightedOrders.get(slotIndex));
    }
    
    public static void addHighlightedOrder(int slotIndex, OrderData orderData) {
        highlightedOrders.put(slotIndex, orderData);
    }
    
    public static void clearHighlightedSlots() {
        highlightedOrders.clear();
    }

    private static void registerScreenRenderEvents() {
        if (!screenEventsRegistered) {
            ScreenEvents.AFTER_INIT.register((client, screen, scaledWidth, scaledHeight) -> {
                if (screen instanceof HandledScreen) {
                    OrderStatusHighlight.clearHighlightedSlots();
                }
            });
            screenEventsRegistered = true;
        }
    }

    @Override
    public void subscribe() {
        registerScreenRenderEvents();
        registerTooltipListener();
        startHealthMonitoring();
    }

    private static void startHealthMonitoring() {
        if (healthMonitorStarted) {
            return;
        }
        
        healthMonitor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "BazaarUtils-TooltipHealthMonitor");
            t.setDaemon(true);
            return t;
        });
        
        healthMonitor.scheduleAtFixedRate(() -> {
            try {
                checkTooltipHealth();
            } catch (Exception e) {
                System.err.println("[BazaarUtils] Error in tooltip health monitor: " + e.getMessage());
            }
        }, HEALTH_CHECK_INTERVAL, HEALTH_CHECK_INTERVAL, TimeUnit.MILLISECONDS);
        
        healthMonitorStarted = true;
        System.out.println("[BazaarUtils] Tooltip health monitoring started");
    }
    
    private static void checkTooltipHealth() {
        long currentTime = System.currentTimeMillis();
        long timeSinceLastActivity = currentTime - lastTooltipActivity;
        
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || !(client.currentScreen instanceof HandledScreen)) {
            lastTooltipActivity = currentTime;
            tooltipCallCount = 0;
            return;
        }
        
        boolean isUnhealthy = false;
        String reason = "";
        
        if (timeSinceLastActivity > ACTIVITY_TIMEOUT) {
            isUnhealthy = true;
            reason = "No tooltip activity for " + (timeSinceLastActivity / 1000) + " seconds";
        }
        
        if (!tooltipCallbackRegistered || currentTooltipCallback == null) {
            isUnhealthy = true;
            reason = "Callback appears unregistered";
        }
        
        if (isUnhealthy) {
            System.out.println("[BazaarUtils] Tooltip callback unhealthy: " + reason + " - Attempting recovery");
            attemptCallbackRecovery();
        } else {
            if (tooltipCallCount > MIN_EXPECTED_CALLS) {
                System.out.println("[BazaarUtils] Tooltip callback healthy - " + tooltipCallCount + " calls in last window");
            }
        }
        
        tooltipCallCount = 0;
    }
    
    private static void attemptCallbackRecovery() {
        try {
            System.out.println("[BazaarUtils] Attempting tooltip callback recovery");
            
            currentTooltipCallback = null;
            tooltipCallbackRegistered = false;
            lastTooltipActivity = System.currentTimeMillis();
            
            registerTooltipListenerInternal();
            
            System.out.println("[BazaarUtils] Tooltip callback recovery completed");
            
        } catch (Exception e) {
            System.err.println("[BazaarUtils] Failed to recover tooltip callback: " + e.getMessage());
        }
    }

    public Option<Boolean> createOption() {
        return Option.<Boolean>createBuilder()
                .name(Text.literal("Order Status Highlight"))
                .description(OptionDescription.of(Text.literal("Adds a colored text in the tooltip of orders that are competitive, matched or outdated.")))
                .binding(false,
                        this::isEnabled,
                        this::setEnabled)
                .controller(BUConfig::createBooleanController)
                .build();
    }

    public Option<Boolean> createFilledHighlightOption() {
        return Option.<Boolean>createBuilder()
                .name(Text.literal("Highlight Filled Orders"))
                .description(OptionDescription.of(Text.literal("Adds a colored text in the tooltip of orders that are filled")))
                .binding(true,
                        this::isFilledHighlightEnabled,
                        this::setFilledHighlightEnabled)
                .controller(BUConfig::createBooleanController)
                .build();
    }

    private void registerTooltipListener() {
        registerTooltipListenerInternal();
    }
    
    private static void registerTooltipListenerInternal() {
        if (tooltipCallbackRegistered && currentTooltipCallback != null) {
            return;
        }
        
        try {
            ItemTooltipCallback newCallback = OrderStatusHighlight::handleTooltip;
            
            currentTooltipCallback = newCallback;
            ItemTooltipCallback.EVENT.register(currentTooltipCallback);
            tooltipCallbackRegistered = true;
            lastTooltipActivity = System.currentTimeMillis();
            
            System.out.println("[BazaarUtils] Tooltip callback registered successfully");
            
        } catch (Exception e) {
            System.err.println("[BazaarUtils] Failed to register tooltip callback: " + e.getMessage());
            tooltipCallbackRegistered = false;
            currentTooltipCallback = null;
        }
    }
    
    private static void handleTooltip(ItemStack stack, net.minecraft.item.Item.TooltipContext context, TooltipType type, List<Text> lines) {
        lastTooltipActivity = System.currentTimeMillis();
        tooltipCallCount++;
        
        OrderStatusHighlight currentInstance = BUConfig.get().orderStatusHighlight;
        if (currentInstance == null || !currentInstance.enabled) return;
        
        if (stack == null || stack.isEmpty() || stack.getItem().getName().getString().contains("GLASS_PANE")) {
            return;
        }

        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || !(client.currentScreen instanceof HandledScreen<?> handledScreen)) {
            return;
        }
        
        for (Text line : lines) {
            String lineText = line.getString();
            if (lineText.contains("FILLED") || lineText.contains("OUTDATED") || 
                lineText.contains("COMPETITIVE") || lineText.contains("MATCHED")) {
                // Our tooltip is already present, skip processing
                return;
            }
        }
        
        int index = -1;
        for (Slot slot : handledScreen.getScreenHandler().slots) {
            if (!(slot.hasStack() && slot.getStack() == stack))
                continue;
            index = slot.getIndex();
        }
        if(index == -1)
            return;

        OrderData order = highlightedOrders.get(index);
        if (order == null) {
            return;
        }

        OrderData.statuses effectiveStatus = currentInstance.getEffectiveStatus(order);
        if (effectiveStatus != null) {
            switch (effectiveStatus) {
                case FILLED:
                    if (currentInstance.filledHighlightEnabled) {
                        lines.add(1, Text.literal("FILLED").formatted(Formatting.GREEN, Formatting.BOLD));
                    }
                    break;
                case OUTDATED:
                    lines.add(1, Text.literal("OUTDATED").formatted(Formatting.RED, Formatting.BOLD));
                    lines.add(2, Text.literal("Market Price: " + order.getPriceInfo().getPrettyString(order.getPriceInfo().getMarketPrice())).formatted(Formatting.RED));
                    break;
                case COMPETITIVE:
                    lines.add(1, Text.literal("COMPETITIVE").formatted(Formatting.GREEN, Formatting.BOLD));
                    break;
                case MATCHED:
                    lines.add(1, Text.literal("MATCHED").formatted(Formatting.YELLOW, Formatting.BOLD));
                    break;
            }
        }
    }
    
    public static void shutdown() {
        if (healthMonitor != null && !healthMonitor.isShutdown()) {
            healthMonitor.shutdown();
            System.out.println("[BazaarUtils] Tooltip health monitor shutdown");
        }
    }
}
