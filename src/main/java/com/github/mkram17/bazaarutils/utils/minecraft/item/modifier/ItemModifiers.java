// Adapted from https://github.com/meowdding/SkyOcean/blob/main/src/main/kotlin/me/owdding/skyocean/features/item/modifier/ItemModifier.kt
package com.github.mkram17.bazaarutils.utils.minecraft.item.modifier;

import com.github.mkram17.bazaarutils.config.BUConfig;
import com.github.mkram17.bazaarutils.events.BUListener;
import com.github.mkram17.bazaarutils.events.screen.ChestLoadedEvent;
import com.github.mkram17.bazaarutils.generated.BazaarUtilsItemModifiers;
import com.github.mkram17.bazaarutils.generated.BazaarUtilsModules;
import com.github.mkram17.bazaarutils.generated.BazaarUtilsPreInitModules;
import com.github.mkram17.bazaarutils.utils.annotations.modules.LateInitModule;
import com.github.mkram17.bazaarutils.utils.config.ToggleableFeature;
import com.github.mkram17.bazaarutils.utils.minecraft.components.LoreParser;
import com.github.mkram17.bazaarutils.utils.minecraft.gui.ScreenContext;
import com.github.mkram17.bazaarutils.utils.minecraft.gui.ScreenManager;
import com.google.common.collect.MapMaker;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.component.ItemLore;
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
import java.util.function.Consumer;
import java.util.stream.Stream;

@LateInitModule
public class ItemModifiers extends BUListener {
    public static List<AbstractItemModifier> MODIFIERS;
    public static final WeakHashMap<ItemStack, List<AbstractItemModifier>> MODIFIED_ITEMS = new WeakHashMap<>();

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
    }

    private static final Set<AbstractItemModifier> DYNAMIC_MODIFIERS = Collections.newSetFromMap(new MapMaker().weakKeys().makeMap());

    public static void registerDynamic(AbstractItemModifier modifier) {
        DYNAMIC_MODIFIERS.add(modifier);
    }

    public static void unregisterDynamic(AbstractItemModifier modifier) {
        DYNAMIC_MODIFIERS.remove(modifier);
    }

    @Subscription
    @OnlyOnSkyBlock
    @MustBeContainer
    private void onContainerChange(InventoryChangeEvent event) {
        tryModify(event.getItem(), AbstractItemModifier.ModifierSource.INVENTORY, event.getSlot());
    }

    @Subscription
    @OnlyOnSkyBlock
    private void onPlayerInventoryChange(PlayerInventoryChangeEvent event) {
        tryModify(event.getItem(), slotToSource(event.getSlot()));
    }

    @Subscription
    @OnlyOnSkyBlock
    private void onScreenInitialized(ScreenInitializedEvent event) {
        Screen screen = event.getScreen();
        if (!(screen instanceof ContainerScreen) && !(screen instanceof InventoryScreen)) return;

        Minecraft client = Minecraft.getInstance();
        if (client.player == null) return;

        reapplyInventory(client.player);

        ScreenEvents.remove(screen).register(s -> {
            if (client.player == null) return;
            client.execute(() -> reapplyInventory(client.player));
        });
    }

    // Some DataTypes/ItemModifiers depend on the ChestLoadedEvent to have fired before they
    // can assert their data. InventoryChangeEvent fires before ChestLoadedEvent,
    // so those stacks are skipped on first pass. This sweeps unmodified
    // slots at LOW priority — after all parsers have stamped their components.
    @Subscription(priority = Subscription.LOWEST)
    @OnlyOnSkyBlock
    private void onChestLoaded(ChestLoadedEvent event) {
        // We only aim to modify items of the container which may of been partialized.
        for (Slot slot : event.getContainerSlots()) {
            if (!slot.hasItem()) continue;

            ItemStack stack = slot.getItem();
            if (MODIFIED_ITEMS.containsKey(stack)) continue;

            ItemStack visual = VisualItemAccessorKt.getVisualItem(stack);
            if (visual != null && MODIFIED_ITEMS.containsKey(visual)) continue;

            tryModify(slot.getItem(), AbstractItemModifier.ModifierSource.INVENTORY, slot);
        }
    }

    private static void reapplyInventory(@NotNull LocalPlayer player) {
        var inventory = player.getInventory();

        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack stack = inventory.getItem(i);

            clear(stack);
            tryModify(stack, slotToSource(i));
        }
    }

    private static AbstractItemModifier.ModifierSource slotToSource(int slot) {
        return (slot >= 36 && slot < 45)
                ? AbstractItemModifier.ModifierSource.HOTBAR
                : AbstractItemModifier.ModifierSource.PLAYER_INVENTORY;
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

    public static void tryModify(ItemStack stack, AbstractItemModifier.ModifierSource source) {
        tryModify(stack, source, null);
    }

    public static void tryModify(ItemStack stack, AbstractItemModifier.ModifierSource source, @Nullable Slot slot) {
        if (stack.isEmpty() || MODIFIED_ITEMS.containsKey(stack)) return;

        List<AbstractItemModifier> candidates = Stream.concat(MODIFIERS.stream(), DYNAMIC_MODIFIERS.stream())
                .sorted(Comparator.comparingInt(AbstractItemModifier::getPriority))
                .toList();

        Optional<ScreenContext> context = ScreenManager.getInstance().current();

        List<AbstractItemModifier> matching = candidates.stream()
                .filter(ToggleableFeature::isEnabled)
                .filter(modifier -> modifier.getModifierSources().contains(source))
                .filter(modifier -> modifier.appliesToScreen(context))
                .filter(modifier -> modifier.appliesTo(stack, slot))
                .toList();

        if (matching.isEmpty()) return;

        List<AbstractItemModifier> applied = new ArrayList<>();

        replace(stack, builder -> {
            for (AbstractItemModifier modifier : matching) {
                boolean[] modified = {false};

                modifier.stackOverride(stack, slot).ifPresent(override -> {
                    builder.applyFrom(override);
                    modified[0] = true;
                });

                modifier.nameOverride(stack, slot).ifPresent(name -> {
                    builder.name(name);
                    modified[0] = true;
                });

                modifier.itemOverride(stack, slot).ifPresent(item -> {
                    builder.item = item;
                    modified[0] = true;
                });

                modifier.backgroundItem(stack, slot).ifPresent(bg -> {
                    builder.setBackgroundItem(bg);
                    modified[0] = true;
                });

                modifier.itemCountOverride(stack, slot).ifPresent(count -> {
                    builder.setCustomSlotComponent(count);
                    modified[0] = true;
                });

                modifier.highlightColor(stack, slot).ifPresent(color -> {
                    builder.setBackgroundColor(color);
                    modified[0] = true;
                });

                Optional<DataComponentPatch> patch = modifier.patchComponents(stack, slot);

                patch.ifPresent(dataComponentPatch -> dataComponentPatch.entrySet().forEach(entry -> {
                    entry.getValue().ifPresent(value -> {
                        @SuppressWarnings("unchecked")
                        DataComponentType<Object> type = (DataComponentType<Object>) entry.getKey();
                        builder.set(type, value);
                        modified[0] = true;
                    });
                }));

                if (modifier.modifyStack(builder.build()).modified()) modified[0] = true;

                if (modified[0]) applied.add(modifier);
            }

            builder.onClick(button -> {
                AbstractItemModifier.Result result = AbstractItemModifier.Result.UNMODIFIED;

                for (AbstractItemModifier modifier : matching) {
                    result = modifier.onClick(stack, button, slot);
                    if (!result.propagateFurther()) break;
                }

                return result.modified() ? kotlin.Unit.INSTANCE : null;
            });
        });

        if (!applied.isEmpty()) {
            ItemStack newVisual = VisualItemAccessorKt.getVisualItem(stack);
            MODIFIED_ITEMS.put(newVisual != null ? newVisual : stack, applied);
        } else {
            VisualItemAccessorKt.replaceVisually(stack, (ItemStack) null);
        }
    }


    public static void clear(ItemStack stack) {
        MODIFIED_ITEMS.remove(stack);
        ItemStack visual = VisualItemAccessorKt.getVisualItem(stack);
        if (visual != null) MODIFIED_ITEMS.remove(visual);
        VisualItemAccessorKt.replaceVisually(stack, (ItemStack) null);
    }

    @Subscription
    @OnlyOnSkyBlock
    private void onTooltip(ItemTooltipEvent event) {
        ItemStack stack = event.getItem();

        List<Component> lines = event.getTooltip();
        if (lines.isEmpty()) return;

        Optional<ScreenContext> context = ScreenManager.getInstance().current();

        List<AbstractItemModifier> applied = new ArrayList<>();
        AbstractItemModifier.Result result = AbstractItemModifier.Result.UNMODIFIED;

        for (AbstractItemModifier modifier : MODIFIERS) {
            if (!modifier.isEnabled() || !modifier.appliesToScreen(context)) continue;
            if (!modifier.appliesTo(stack)) continue;

            result = modifier.modifyLore(stack, lines, result);
            if (result.modified()) applied.add(modifier);
            if (!result.propagateFurther()) break;
        }

        List<AbstractItemModifier> standardModifiers = MODIFIED_ITEMS.getOrDefault(stack, List.of());
        if (applied.isEmpty() && standardModifiers.isEmpty()) return;

        applyDefaults(lines);
    }

    @Subscription
    @OnlyOnSkyBlock
    private void onGatherTooltipComponents(GatherItemTooltipComponentsEvent event) {
        ItemStack stack = event.getItem();
        Optional<ScreenContext> context = ScreenManager.getInstance().current();

        for (AbstractItemModifier modifier : MODIFIERS) {
            if (!modifier.isEnabled() || !modifier.appliesToScreen(context)) continue;
            if (!modifier.appliesTo(stack)) continue;

            AbstractItemModifier.Result result = modifier.appendComponents(stack, event.getComponents());
            if (!result.propagateFurther()) break;
        }
    }

    private static void applyDefaults(List<Component> lines) {
        switch (BUConfig.MODIFY_INDICATOR) {
            case PREFIX -> lines.set(0, Component.empty().append(AbstractItemModifier.INDICATOR_WITH_SPACE).append(lines.getFirst()));
            case SUFFIX -> lines.set(0, Component.empty().append(lines.getFirst()).append(AbstractItemModifier.SPACE_WITH_INDICATOR));
            case LORE -> {
                lines.add(Component.empty());
                lines.add(AbstractItemModifier.INDICATOR_LABEL_LINE);
            }
            case DISABLED -> {}
        }
    }
}