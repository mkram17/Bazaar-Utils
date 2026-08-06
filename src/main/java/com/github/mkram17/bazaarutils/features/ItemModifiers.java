package com.github.mkram17.bazaarutils.features;

import com.github.mkram17.bazaarutils.config.BUConfig;
import com.github.mkram17.bazaarutils.events.BUListener;
import com.github.mkram17.bazaarutils.events.minecraft.ContainerLoadedEvent;
import com.github.mkram17.bazaarutils.generated.BazaarUtilsItemModifiers;
import com.github.mkram17.bazaarutils.generated.BazaarUtilsModules;
import com.github.mkram17.bazaarutils.generated.BazaarUtilsPreInitModules;
import com.github.mkram17.bazaarutils.utils.Result;
import com.github.mkram17.bazaarutils.utils.ToggleableFeature;
import com.github.mkram17.bazaarutils.utils.Util;
import com.github.mkram17.bazaarutils.utils.annotations.modules.LateInitModule;
import com.github.mkram17.bazaarutils.utils.minecraft.gui.ScreenContext;
import com.github.mkram17.bazaarutils.utils.minecraft.gui.ScreenManager;
import com.github.mkram17.bazaarutils.utils.minecraft.item.modifier.AbstractItemModifier;
import com.github.mkram17.bazaarutils.utils.minecraft.item.modifier.ModifyIndicator;
import com.google.common.collect.MapMaker;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.TooltipDisplay;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import tech.thatgravyboat.skyblockapi.api.events.base.Subscription;
import tech.thatgravyboat.skyblockapi.api.events.base.predicates.MustBeContainer;
import tech.thatgravyboat.skyblockapi.api.events.base.predicates.OnlyOnSkyBlock;
import tech.thatgravyboat.skyblockapi.api.events.minecraft.ui.GatherItemTooltipComponentsEvent;
import tech.thatgravyboat.skyblockapi.api.events.screen.*;
import tech.thatgravyboat.skyblockapi.api.item.VisualItemAccessorKt;
import tech.thatgravyboat.skyblockapi.utils.builders.ItemBuilder;

import java.util.*;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Consumer;
import java.util.stream.Stream;

@LateInitModule
public class ItemModifiers extends BUListener {
    public static List<AbstractItemModifier> MODIFIERS = new ArrayList<>(List.of());
    public static final ConcurrentMap<ItemStack, List<AbstractItemModifier>> MODIFIED_ITEMS = new MapMaker().weakKeys().makeMap();

    public ItemModifiers() {
        MODIFIERS = Stream.of(
                        BazaarUtilsPreInitModules.collected,
                        BazaarUtilsModules.collected,
                        BazaarUtilsItemModifiers.collected
                ).flatMap(List::stream)
                .filter(it -> it instanceof AbstractItemModifier)
                .map(it -> (AbstractItemModifier) it)
                .sorted(Comparator.comparingInt(AbstractItemModifier::getPriority))
                .toList();

        Util.logMessage("ItemModifiers initialized — %d modifiers registered".formatted(MODIFIERS.size()));
    }

    private static final Set<AbstractItemModifier> DYNAMIC_MODIFIERS = Collections.newSetFromMap(new MapMaker().weakKeys().makeMap());

    public static void registerDynamic(AbstractItemModifier modifier) {
        DYNAMIC_MODIFIERS.add(modifier);
    }

    public static void unregisterDynamic(AbstractItemModifier modifier) {
        DYNAMIC_MODIFIERS.remove(modifier);
    }

    private static Stream<AbstractItemModifier> allModifiers() {
        return Stream.concat(MODIFIERS.stream(), DYNAMIC_MODIFIERS.stream())
                .sorted(Comparator.comparingInt(AbstractItemModifier::getPriority));
    }


    private sealed interface DataMarker<T> permits DataMarker.Default, DataMarker.ForComponent {
        Default<ItemStack> STACK = new Default<>();
        Default<Item> ITEM = new Default<>();
        Default<ItemStack> BACKGROUND = new Default<>();
        Default<Component> NAME = new Default<>();
        Default<Component> COUNT = new Default<>();
        Default<Integer> BORDER_COLOR = new Default<>();
        Default<Integer> BG_COLOR = new Default<>();
        Default<Integer> FG_COLOR = new Default<>();

        final class Default<T> implements DataMarker<T> {}

        record ForComponent<T>(DataComponentType<T> type) implements DataMarker<T> {}

        static <T> boolean put(Map<DataMarker<?>, Object> map, DataMarker<T> key, @Nullable T value) {
            if (value == null || map.containsKey(key)) return false;

            map.put(key, value);

            return true;
        }

        @SuppressWarnings("unchecked")
        static <T> Optional<T> get(Map<DataMarker<?>, Object> map, DataMarker<T> key, Class<T> type) {
            Object v = map.get(key);

            return type.isInstance(v) ? Optional.of((T) v) : Optional.empty();
        }
    }


    @Subscription
    @OnlyOnSkyBlock
    @MustBeContainer
    private void onContainerChange(InventoryChangeEvent event) {
        var context = ScreenManager.getInstance().currentOrNull();
        tryModify(event.getItem(), AbstractItemModifier.ModifierSource.CONTAINER, context, event.getSlot());
    }

    @Subscription
    @OnlyOnSkyBlock
    private void onPlayerInventoryChange(PlayerInventoryChangeEvent event) {
        var context = ScreenManager.getInstance().currentOrNull();
        tryModify(event.getItem(), playerSlotToSource(event.getSlotIndex(), PlayerHotbarChangeEvent.FIRST_HOTBAR_SLOT, context), context, event.getInventorySlot());
    }

    @Subscription
    @OnlyOnSkyBlock
    private void onHotbarChange(PlayerHotbarChangeEvent event) {
        var context = ScreenManager.getInstance().currentOrNull();
        tryModify(event.getItem(), playerSlotToSource(event.getSlotIndex(), 0, context), context, event.getInventorySlot());
    }

    @Subscription
    @OnlyOnSkyBlock
    private void onEquipmentChange(PlayerEquipmentChangeEvent event) {
        var context = ScreenManager.getInstance().currentOrNull();
        tryModify(event.getItem(), AbstractItemModifier.ModifierSource.EQUIPMENT, context, null);
    }

    @Subscription
    @OnlyOnSkyBlock
    private void onScreenInitialized(ScreenInitializedEvent event) {
        Screen screen = event.getScreen();
        if (!(screen instanceof ContainerScreen) && !(screen instanceof InventoryScreen)) return;

        Minecraft client = Minecraft.getInstance();
        if (client.player == null) return;

        var context = ScreenManager.getInstance().currentOrNull();
        reapplyInventory(client.player, context);

        ScreenEvents.remove(screen).register(s -> {
            LocalPlayer player = client.player;
            if (player == null) return;

            Util.tickExecuteLater(1, () -> {
                var currentContext = ScreenManager.getInstance().currentOrNull();

                reapplyInventory(player, currentContext);
            });
        });
    }

    // Some DataTypes/ItemModifiers depend on the ChestLoadedEvent to have fired before they
    // can assert their data. InventoryChangeEvent fires before ChestLoadedEvent, so those
    // modifiers are skipped on first pass — including on stacks a broader modifier already
    // claimed. This clears and re-runs every slot at LOWEST priority, after all parsers
    // have stamped their components.
    @Subscription(priority = Subscription.LOWEST)
    @OnlyOnSkyBlock
    private void onContainerLoaded(ContainerLoadedEvent event) {
        reapplySlots(event.getContainerSlots(), AbstractItemModifier.ModifierSource.CONTAINER, event.asContext());
        reapplySlots(event.getPlayerSlots(), AbstractItemModifier.ModifierSource.PLAYER_INVENTORY, event.asContext());
    }

    private static void reapplySlots(List<Slot> slots, AbstractItemModifier.ModifierSource source, @Nullable ScreenContext context) {
        for (Slot slot : slots) {
            if (!slot.hasItem()) continue;

            ItemStack stack = slot.getItem();

            clear(stack);
            tryModify(stack, source, context, slot);
        }
    }

    // MODIFIED_ITEMS is keyed by the visual stack tryModify produced, so the original
    // has to be resolved through the accessor before it can be looked up.
    private static boolean isModified(ItemStack stack) {
        if (MODIFIED_ITEMS.containsKey(stack)) return true;

        ItemStack visual = VisualItemAccessorKt.getVisualItem(stack);

        return visual != null && MODIFIED_ITEMS.containsKey(visual);
    }

    public static void clear(ItemStack stack) {
        MODIFIED_ITEMS.remove(stack);

        ItemStack visual = VisualItemAccessorKt.getVisualItem(stack);
        if (visual != null) MODIFIED_ITEMS.remove(visual);

        VisualItemAccessorKt.replaceVisually(stack, (ItemStack) null);
    }


    public static void replace(ItemStack stack, Consumer<ItemBuilder> init) {
        VisualItemAccessorKt.replaceVisually(stack, builder -> {
            builder.copyFrom(stack);

            TooltipDisplay existing = stack.get(DataComponents.TOOLTIP_DISPLAY);
            if (existing != null) builder.set(DataComponents.TOOLTIP_DISPLAY, new TooltipDisplay(false, existing.hiddenComponents()));

            init.accept(builder);

            return kotlin.Unit.INSTANCE;
        });
    }

    private static void reapplyInventory(@NotNull LocalPlayer player, @Nullable ScreenContext context) {
        var menu = (context != null && context.screen() instanceof ContainerScreen container)
                ? container.getMenu()
                : player.inventoryMenu;

        IdentityHashMap<ItemStack, Slot> slotByStack = new IdentityHashMap<>();
        for (Slot slot : menu.slots) {
            if (slot.container instanceof Inventory && slot.hasItem()) {
                slotByStack.put(slot.getItem(), slot);
            }
        }

        var inventory = player.getInventory();

        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack stack = inventory.getItem(i);
            if (stack.isEmpty()) continue;

            clear(stack);
            tryModify(stack, playerSlotToSource(i, context), context, slotByStack.get(stack));
        }

        for (EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
            ItemStack stack = player.getItemBySlot(slot);
            if (stack.isEmpty()) continue;

            clear(stack);
            tryModify(stack, AbstractItemModifier.ModifierSource.EQUIPMENT, context, null);
        }

        ItemStack offhand = player.getItemInHand(InteractionHand.OFF_HAND);
        if (!offhand.isEmpty()) {
            clear(offhand);
            tryModify(offhand, AbstractItemModifier.ModifierSource.PLAYER_INVENTORY, context, null);
        }
    }

    public static void tryModify(ItemStack stack, AbstractItemModifier.ModifierSource source, @Nullable ScreenContext context, @Nullable Slot slot) {
        if (stack.isEmpty() || isModified(stack)) return;

        List<AbstractItemModifier> matching = allModifiers()
                .filter(ToggleableFeature::isEnabled)
                .filter(modifier -> modifier.getModifierSources().contains(source))
                .filter(modifier -> modifier.appliesToScreen(context))
                .filter(modifier -> modifier.appliesTo(stack, slot, context))
                .toList();

        if (matching.isEmpty()) return;

        Map<DataMarker<?>, Object> props = new LinkedHashMap<>();
        List<AbstractItemModifier> applied = new ArrayList<>();

        for (AbstractItemModifier modifier : matching) {
            int sizeBefore = props.size();

            DataMarker.put(props, DataMarker.STACK, modifier.stackOverride(stack, slot).orElse(null));
            DataMarker.put(props, DataMarker.ITEM, modifier.itemOverride(stack, slot).orElse(null));
            DataMarker.put(props, DataMarker.BACKGROUND, modifier.backgroundItem(stack, slot).orElse(null));
            DataMarker.put(props, DataMarker.NAME, modifier.nameOverride(stack, slot).orElse(null));
            DataMarker.put(props, DataMarker.COUNT, modifier.itemCountOverride(stack, slot).orElse(null));
            DataMarker.put(props, DataMarker.BORDER_COLOR, modifier.borderColor(stack, slot).orElse(null));
            DataMarker.put(props, DataMarker.BG_COLOR, modifier.backgroundColor(stack, slot).orElse(null));
            DataMarker.put(props, DataMarker.FG_COLOR, modifier.foregroundColor(stack, slot).orElse(null));

            modifier.patchComponents(stack, slot).ifPresent(patch ->
                    patch.entrySet().forEach(entry -> entry.getValue().ifPresent(value -> {
                        @SuppressWarnings("unchecked")
                        DataComponentType<Object> type = (DataComponentType<Object>) entry.getKey();

                        DataMarker.put(props, new DataMarker.ForComponent<>(type), value);
                    }))
            );

            if (props.size() > sizeBefore) applied.add(modifier);
        }

        replace(stack, builder -> {
            DataMarker.get(props, DataMarker.STACK, ItemStack.class).ifPresent(builder::applyFrom);
            DataMarker.get(props, DataMarker.ITEM, Item.class).ifPresent(i -> builder.item = i);
            DataMarker.get(props, DataMarker.BACKGROUND, ItemStack.class).ifPresent(builder::setBackgroundItem);
            DataMarker.get(props, DataMarker.NAME, Component.class).ifPresent(builder::name);
            DataMarker.get(props, DataMarker.COUNT, Component.class).ifPresent(builder::setCustomSlotComponent);
            DataMarker.get(props, DataMarker.BORDER_COLOR, Integer.class).ifPresent(builder::setBorderColor);
            DataMarker.get(props, DataMarker.BG_COLOR, Integer.class).ifPresent(builder::setBackgroundColor);
            DataMarker.get(props, DataMarker.FG_COLOR, Integer.class).ifPresent(builder::setForegroundColor);

            // Apply all DataComponent patch entries.
            props.forEach((marker, value) -> {
                if (marker instanceof DataMarker.ForComponent<?>(DataComponentType<?> data)) {
                    @SuppressWarnings("unchecked")
                    DataComponentType<Object> type = (DataComponentType<Object>) data;

                    builder.set(type, value);
                }
            });

            builder.onClick(button -> {
                boolean acted = false;

                for (AbstractItemModifier modifier : matching) {
                    Result result = modifier.onClick(stack, button, slot, context);
                    acted |= result.acted();

                    if (!result.propagate()) break;
                }

                // Any handler acting has to swallow the click, otherwise an earlier
                // modifier's action is lost and the click still reaches the server.
                return acted ? kotlin.Unit.INSTANCE : null;
            });
        });

        ItemStack updated = VisualItemAccessorKt.getVisualItem(stack);
        MODIFIED_ITEMS.put(updated != null ? updated : stack, applied);
    }

    @Subscription
    @OnlyOnSkyBlock
    private void onTooltip(ItemTooltipEvent event) {
        ItemStack stack = event.getItem();
        List<Component> lines = event.getTooltip();
        if (lines.isEmpty()) return;

        var context = ScreenManager.getInstance().currentOrNull();
        AbstractItemModifier.ModifierSource source = resolveTooltipSource(stack, context);

        List<AbstractItemModifier> applied = new ArrayList<>();
        Result result = Result.UNMODIFIED;

        for (AbstractItemModifier modifier : MODIFIERS) {
            if (!modifier.isEnabled() || !modifier.getModifierSources().contains(source)) continue;
            if (!modifier.appliesToScreen(context)) continue;
            if (!modifier.appliesTo(stack)) continue;

            result = modifier.modifyLore(stack, lines, result, context);
            if (result.acted()) applied.add(modifier);
            if (!result.propagate()) break;
        }

        List<AbstractItemModifier> standardModifiers = MODIFIED_ITEMS.getOrDefault(stack, List.of());
        if (applied.isEmpty() && standardModifiers.isEmpty()) return;

        Stream.concat(applied.stream(), standardModifiers.stream())
                .map(modifier -> BUConfig.MODIFY_INDICATOR.resolve(modifier))
                .filter(placement -> !(placement instanceof ModifyIndicator.IndicatorPlacement.AtModification))
                .filter(placement -> !(placement instanceof ModifyIndicator.IndicatorPlacement.Disabled))
                .distinct()
                .forEach(placement -> ModifyIndicator.applyPlacement(lines, placement));
    }

    @Subscription
    @OnlyOnSkyBlock
    private void onGatherTooltipComponents(GatherItemTooltipComponentsEvent event) {
        ItemStack stack = event.getItem();

        var context = ScreenManager.getInstance().currentOrNull();
        AbstractItemModifier.ModifierSource source = resolveTooltipSource(stack, context);

        for (AbstractItemModifier modifier : MODIFIERS) {
            if (!modifier.isEnabled() || !modifier.getModifierSources().contains(source)) continue;
            if (!modifier.appliesToScreen(context)) continue;
            if (!modifier.appliesTo(stack)) continue;

            Result result = modifier.appendComponents(stack, event.getComponents(), context);
            if (!result.propagate()) break;
        }
    }

    private static AbstractItemModifier.ModifierSource resolveTooltipSource(ItemStack stack, @Nullable ScreenContext context) {
        if (context != null) {
            Optional<ContainerScreen> containerScreen = context.as(ContainerScreen.class);
            if (containerScreen.isPresent()) {
                for (Slot slot : containerScreen.get().getMenu().slots) {
                    if (!slot.hasItem()) continue;

                    ItemStack item = slot.getItem();
                    if (item != stack && VisualItemAccessorKt.getVisualItem(item) != stack) continue;

                    if (!(slot.container instanceof Inventory)) {
                        return AbstractItemModifier.ModifierSource.CONTAINER;
                    }

                    break;
                }
            }
        }

        LocalPlayer player = Minecraft.getInstance().player;
        if (player != null) {
            var inventory = player.getInventory();
            for (int i = 0; i < inventory.getContainerSize(); i++) {
                ItemStack item = inventory.getItem(i);

                if (item == stack || VisualItemAccessorKt.getVisualItem(item) == stack) {
                    return playerSlotToSource(i, context);
                }
            }
        }

        return AbstractItemModifier.ModifierSource.PLAYER_INVENTORY;
    }

    private static AbstractItemModifier.ModifierSource playerSlotToSource(int slot, @Nullable ScreenContext context) {
        return playerSlotToSource(slot, 0, context);
    }

    private static AbstractItemModifier.ModifierSource playerSlotToSource(int slot, int start, @Nullable ScreenContext context) {
        boolean isInventoryOpen = context != null && (context.screen() instanceof ContainerScreen || context.screen() instanceof InventoryScreen);

        return (slot >= start && slot <= start + 8 && !isInventoryOpen)
                ? AbstractItemModifier.ModifierSource.HOTBAR
                : AbstractItemModifier.ModifierSource.PLAYER_INVENTORY;
    }
}
