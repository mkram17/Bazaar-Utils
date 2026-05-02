package com.github.mkram17.bazaarutils.utils.bazaar;

import com.github.mkram17.bazaarutils.events.minecraft.ContainerLoadedEvent;
import com.github.mkram17.bazaarutils.events.BUListener;
import com.github.mkram17.bazaarutils.events.predicates.OnlyBazaarScreen;
import com.github.mkram17.bazaarutils.events.predicates.OnlyWhenEnabled;
import com.github.mkram17.bazaarutils.features.ItemModifiers;
import com.github.mkram17.bazaarutils.features.gui.inventory.restrictions.controls.RestrictionControl;
import com.github.mkram17.bazaarutils.utils.BazaarLogger;
import com.github.mkram17.bazaarutils.utils.PlayerLogger;
import com.github.mkram17.bazaarutils.utils.Result;
import com.github.mkram17.bazaarutils.utils.minecraft.ItemInfo;
import com.github.mkram17.bazaarutils.utils.minecraft.components.CustomDataComponents;
import com.github.mkram17.bazaarutils.utils.minecraft.gui.ScreenContext;
import com.github.mkram17.bazaarutils.utils.minecraft.gui.ScreenManager;
import com.github.mkram17.bazaarutils.utils.minecraft.item.modifier.AbstractItemModifier;
import com.github.mkram17.bazaarutils.utils.minecraft.item.modifier.LoreModifier;
import com.github.mkram17.bazaarutils.utils.minecraft.item.modifier.ModifyIndicator;
import lombok.Getter;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import tech.thatgravyboat.skyblockapi.api.events.base.Subscription;
import tech.thatgravyboat.skyblockapi.api.events.base.predicates.OnlyOnSkyBlock;
import tech.thatgravyboat.skyblockapi.api.events.screen.ScreenInitializedEvent;
import tech.thatgravyboat.skyblockapi.api.item.VisualItemAccessorKt;

import java.util.List;
import java.util.Optional;

public abstract class RestrictionHelper<T extends RestrictionHelper.RestrictionState> extends BUListener implements LoreModifier, AbstractItemModifier {
    protected static final BazaarLogger LOG = BazaarLogger.of(RestrictionHelper.class);

    public interface RestrictionState {
        @NotNull
        ItemInfo targetItem();
        @NotNull
        List<RestrictionControl<?>> triggeredRestrictors();
    }

    @Getter
    protected String name;

    protected abstract int getClicksOverride();
    protected abstract String getMessagePrefix();
    protected abstract List<RestrictionControl<?>> getRestrictors();

    @Getter
    private transient int clicks = 0;
    private transient boolean isRestricted = true;
    private transient Optional<T> state = Optional.empty();

    protected abstract Optional<T> makeState(ContainerLoadedEvent event);

    protected void resetState() {
        state = Optional.empty();
    }

    public RestrictionHelper(String name) {
        super();
        this.name = name;
    }

    @Subscription(inherited = true)
    @OnlyWhenEnabled
    @OnlyOnSkyBlock
    public void onScreenInitialized(ScreenInitializedEvent event) {
        clicks = 0;
        isRestricted = true;
        state = Optional.empty();
    }

    @Subscription(inherited = true)
    @OnlyWhenEnabled
    @OnlyOnSkyBlock
    @OnlyBazaarScreen(useConstrainsInterface = true)
    public void onContainerLoaded(ContainerLoadedEvent event) {
        state = makeState(event);
        isRestricted = state.map(state -> !state.triggeredRestrictors().isEmpty()).orElse(true);
        clicks = 0;
    }

    @Override
    public boolean appliesTo(ItemStack stack, @Nullable Slot slot, @Nullable ScreenContext context) {
        int remaining = getClicksOverride() - getClicks();

        if (!isRestricted || remaining <= 0) return false;

        return slot != null && state
                .map(RestrictionState::targetItem)
                .map(info -> info.slotIndex() == slot.getContainerSlot())
                .orElse(false);
    }

    @Override
    public boolean appliesTo(ItemStack stack) {
        int remaining = getClicksOverride() - getClicks();

        if (!isRestricted || remaining <= 0) return false;

        return state.map(RestrictionState::targetItem)
                .map(info -> {
                    var context = ScreenManager.getInstance().currentOrNull();
                    if (context == null) return false;

                    var screen = context.as(AbstractContainerScreen.class);
                    if (screen.isEmpty()) return false;

                    for (Slot slot : screen.get().getMenu().slots) {
                        if (slot.getContainerSlot() != info.slotIndex()) continue;

                        ItemStack original = slot.getItem();
                        ItemStack visual = VisualItemAccessorKt.getVisualItem(original);

                        if (original == stack || (visual != null && visual == stack)) return true;
                    }

                    return false;
                })
                .orElse(false);
    }

    @Override
    public Result onClick(ItemStack stack, int button, @Nullable Slot slot, @Nullable ScreenContext context) {
        int remaining = getClicksOverride() - getClicks();

        if (isRestricted && remaining > 0) {
            clicks++;

            retriggerModifier();
            state.ifPresent(state -> PlayerLogger.send(getMessage(state)));

            return Result.CONSUMED;
        }

        LOG.info("%s: override threshold reached ({}) — action proceeding", name, getClicksOverride());

        return Result.UNMODIFIED;
    }

    @Override
    public ModifyIndicator.IndicatorPlacement indicatorPlacement() {
        return ModifyIndicator.IndicatorPlacement.AT_MODIFICATION;
    }

    @Override
    public Result modifyLore(ItemStack stack, List<Component> lore, @Nullable Result previous, @Nullable ScreenContext context) {
        int remaining = getClicksOverride() - getClicks();

        if (!isRestricted || remaining <= 0) return Result.UNMODIFIED;

        return withMerger(lore, merger -> {
            merger.copy(); // item name

            merger.add(withAtModificationIndicator(
                    Component.literal("Safety clicks remaining: " + remaining)
                            .withStyle(style -> style
                                    .withColor(ChatFormatting.YELLOW)
                                    .withItalic(false)
                                    .withBold(false))));
            merger.add(Component.literal(""));

            return Result.HANDLED;
        });
    }

    @Override
    public Optional<DataComponentPatch> patchComponents(ItemStack stack, @Nullable Slot slot) {
        int remaining = getClicksOverride() - getClicks();

        if (!isRestricted || remaining <= 0) return Optional.empty();

        return Optional.of(DataComponentPatch.builder()
                .set(CustomDataComponents.CUSTOM_SIZE, String.valueOf(remaining))
                .build());
    }

    protected String getMessage(T state) {
        StringBuilder message = new StringBuilder(getMessagePrefix());

        for (RestrictionControl<?> control : state.triggeredRestrictors()) {
            message.append(" ").append(control.describeRule());
        }

        message.append(" (Safety Clicks Left: ").append(getClicksOverride() - getClicks()).append(")");
        return message.toString();
    }

    private void retriggerModifier() {
        state.map(RestrictionState::targetItem)
                .ifPresent(info -> {
                    var context = ScreenManager.getInstance().currentOrNull();
                    if (context == null) return;

                    var screen = context.as(AbstractContainerScreen.class);
                    if (screen.isEmpty()) return;

                    screen.get().getMenu().slots.stream()
                            .filter(slot -> slot.getContainerSlot() == info.slotIndex())
                            .findFirst()
                            .ifPresent(slot -> {
                                ItemModifiers.clear(slot.getItem());
                                ItemModifiers.tryModify(slot.getItem(), ModifierSource.CONTAINER, context, slot);
                            });
                });
    }
}