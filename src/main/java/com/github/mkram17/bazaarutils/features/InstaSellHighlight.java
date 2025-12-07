package com.github.mkram17.bazaarutils.features;

import com.github.mkram17.bazaarutils.events.ChestLoadedEvent;
import com.github.mkram17.bazaarutils.events.handlers.BUListener;
import com.github.mkram17.bazaarutils.misc.SlotHighlightCache;
import com.github.mkram17.bazaarutils.misc.orderinfo.OrderInfoContainer;
import com.github.mkram17.bazaarutils.utils.GUIUtils;
import com.github.mkram17.bazaarutils.utils.Util;
import com.github.mkram17.bazaarutils.utils.InstaSellUtil;
import com.github.mkram17.bazaarutils.utils.ScreenInfo;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.ColorHelper;

import java.util.*;

import static com.github.mkram17.bazaarutils.BazaarUtils.EVENT_BUS;

@NoArgsConstructor
public class InstaSellHighlight implements BUListener {

    @Getter @Setter
    private boolean enabled;
    private transient final List<Integer> highlightedSlotIndexes = new ArrayList<>();

    public InstaSellHighlight(boolean enabled) {
        this.enabled = enabled;
    }

    @EventHandler
    private void onScreenLoad(ChestLoadedEvent e) {
        highlightedSlotIndexes.clear();

        if (!enabled || !ScreenInfo.getCurrentScreenInfo().inMenu(ScreenInfo.BazaarMenuType.BAZAAR_MAIN_PAGE)) {
            return;
        }

        Optional<PlayerInventory> optionalInventory = getInventory();
        if (optionalInventory.isEmpty()) {
            Util.notifyError("Failed to get player inventory.", new Throwable());
            return;
        }
        PlayerInventory inventory = optionalInventory.get();

        List<OrderInfoContainer> instaSellOrders = InstaSellUtil.getInstaSellOrders(e.getItemStacks());
        List<String> names = instaSellOrders.stream()
                .map(OrderInfoContainer::getName)
                .distinct()
                .toList();

        List<ItemStack> inventoryStacks = getInventoryStacks(names);

        highlightedSlotIndexes.addAll(
                inventoryStacks.stream()
                        .filter(itemStack -> !itemStack.isEmpty())
                        .map(itemStack -> GUIUtils.getSlotFromItemStack(inventory, itemStack))
                        .toList()
        );
    }

    private Optional<PlayerInventory> getInventory(){
        ClientPlayerEntity player = MinecraftClient.getInstance().player;
        if (player == null) {
            Util.notifyError("Player is null, cannot get inventory stacks.", new Throwable());
            return Optional.empty();
        }

        return Optional.of(player.getInventory());
    }


    private List<ItemStack> getInventoryStacks(List<String> names){
        List<ItemStack> inventoryStacks = new ArrayList<>();
        var inventoryOpt = getInventory();
        if (inventoryOpt.isEmpty()) return Collections.emptyList();
        var inventory = inventoryOpt.get();
        var stacks = inventory.getMainStacks();

        stacks.forEach(itemStack -> {
            if(itemStack.isEmpty()) return;
            String itemName = itemStack.getName().getString();
            if (names.stream().anyMatch(name -> itemName.toLowerCase().contains(name.toLowerCase()))) {
                inventoryStacks.add(itemStack);
            }
        });
        return inventoryStacks;
    }
    public void updateHighlightCache(){
        if (!enabled) return;
        for(Integer index : highlightedSlotIndexes) {
            getColorFromIndex(index).ifPresent(instaSellHighlightColor -> SlotHighlightCache.instaSellHighlightCache.computeIfAbsent(index, (k) -> instaSellHighlightColor));
        }
    }

    @Override
    public void subscribe() {
        EVENT_BUS.subscribe(this);
    }
    public OptionalInt getColorFromIndex(int slotIndex) {
        if (!highlightedSlotIndexes.contains(slotIndex))
            return OptionalInt.empty();

        float transparency = 0.7f;
        int argb = ColorHelper.fromFloats(transparency, 1f, 1f, 0f); // yellow
        return OptionalInt.of(argb);
    }
}
