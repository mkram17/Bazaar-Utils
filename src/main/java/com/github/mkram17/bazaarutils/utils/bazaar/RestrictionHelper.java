package com.github.mkram17.bazaarutils.utils.bazaar;

import com.github.mkram17.bazaarutils.events.screen.ChestLoadedEvent;
import com.github.mkram17.bazaarutils.events.BUListener;
import com.github.mkram17.bazaarutils.features.gui.inventory.restrictions.controls.RestrictionControl;
import com.github.mkram17.bazaarutils.misc.NotificationType;
import com.github.mkram17.bazaarutils.utils.BazaarLogger;
import com.github.mkram17.bazaarutils.utils.PlayerActionUtil;
import com.github.mkram17.bazaarutils.utils.PlayerLogger;
import com.github.mkram17.bazaarutils.utils.annotations.events.OnlyWhenEnabled;
import com.github.mkram17.bazaarutils.utils.minecraft.ItemInfo;
import com.github.mkram17.bazaarutils.utils.minecraft.components.CustomDataComponents;
import com.github.mkram17.bazaarutils.utils.minecraft.gui.ScreenContext;
import com.github.mkram17.bazaarutils.utils.minecraft.gui.ScreenManager;
import com.github.mkram17.bazaarutils.utils.minecraft.item.modifier.AbstractItemModifier;
import com.github.mkram17.bazaarutils.utils.minecraft.item.modifier.ItemModifiers;
import com.github.mkram17.bazaarutils.utils.minecraft.item.modifier.LoreModifier;
import lombok.Getter;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.inventory.Slot;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import tech.thatgravyboat.skyblockapi.api.events.base.Subscription;
import tech.thatgravyboat.skyblockapi.api.events.base.predicates.IgnoreFiller;
import tech.thatgravyboat.skyblockapi.api.events.base.predicates.MustBeContainer;
import tech.thatgravyboat.skyblockapi.api.events.base.predicates.OnlyOnSkyBlock;
import tech.thatgravyboat.skyblockapi.api.events.screen.SlotClickEvent;
import tech.thatgravyboat.skyblockapi.api.item.VisualItemAccessorKt;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

public abstract class RestrictionHelper<T extends RestrictionHelper.RestrictionState> extends BUListener implements AbstractItemModifier, LoreModifier {
    private static final BazaarLogger LOG = BazaarLogger.of(RestrictionHelper.class);

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

    protected abstract Optional<T> makeState(ChestLoadedEvent event);

    protected void resetState() {
        state = Optional.empty();
    }

    public RestrictionHelper(String name) {
        super();
        this.name = name;
    }

    @Override
    protected void registerFabricEvents() {
        ScreenEvents.AFTER_INIT.register((client, screen, width, height) -> {
            clicks = 0;
            isRestricted = true;
            state = Optional.empty();
        });
    }

    @Subscription(inherited = true)
    @OnlyWhenEnabled
    @OnlyOnSkyBlock
    public void onChestLoaded(ChestLoadedEvent event) {
        if (!(isEnabled() && inCorrectScreen())) {
            resetState();
            return;
        }

        state = makeState(event);
        isRestricted = state.map(state -> !state.triggeredRestrictors().isEmpty()).orElse(true);
        clicks = 0;

        if (state.isEmpty()) {
            LOG.info("{}: makeState returned empty", name);
        } else {
            List<RestrictionControl<?>> triggered = state.get().triggeredRestrictors();

            if (triggered.isEmpty()) {
                PlayerLogger.debug("%s: loaded — no rules triggered, action unrestricted".formatted(name), NotificationType.FEATURE);
            } else {
                String rules = triggered.stream()
                        .map(RestrictionControl::describeRule)
                        .collect(Collectors.joining(", "));

                PlayerLogger.debug("%s: loaded — restricted by [%s]".formatted(name, rules), NotificationType.FEATURE);
            }
        }
    }

    @Subscription(inherited = true)
    @OnlyWhenEnabled
    @OnlyOnSkyBlock
    @MustBeContainer
    @IgnoreFiller
    public void onSlotClicked(SlotClickEvent event) {
        if (!inCorrectScreen() || !isEnabled()) return;

        boolean isRestrictedSlot = state.map(RestrictionState::targetItem)
                .map(info -> info.slotIndex() == event.getSlot().getContainerSlot())
                .orElse(false);

        if (!isRestrictedSlot) return;

        if (isRestricted && clicks < getClicksOverride()) {
            clicks++;
            PlayerActionUtil.notifyAll(getMessage(state.get()));
            retriggerModifier();
            event.cancel();
        } else if (isRestricted) {
            LOG.info("%s: override threshold reached (%d) — action proceeding".formatted(name, getClicksOverride()), NotificationType.FEATURE);
        }
    }

    public abstract boolean inCorrectScreen();

    protected String getMessage(T state) {
        StringBuilder message = new StringBuilder(getMessagePrefix());

        for (RestrictionControl<?> control : state.triggeredRestrictors()) {
            message.append(" ").append(control.describeRule());
        }

        message.append(" (Safety Clicks Left: ").append(getClicksOverride() - getClicks()).append(")");

        return message.toString();
    }

    @Override
    public List<ModifierSource> getModifierSources() {
        return List.of(ModifierSource.INVENTORY);
    }

    @Override
    public boolean appliesToScreen(Optional<ScreenContext> context) {
        return inCorrectScreen();
    }

    @Override
    public boolean appliesTo(ItemStack stack, @Nullable Slot slot) {
        return slot != null && state
                .map(RestrictionState::targetItem)
                .map(info -> info.slotIndex() == slot.getContainerSlot())
                .orElse(false);
    }

    @Override
    public boolean appliesTo(ItemStack stack) {
        if (!isEnabled() || !inCorrectScreen()) return false;

        return state.map(RestrictionState::targetItem)
                .map(info -> {
                    AbstractContainerScreen<?> screen = ScreenManager.getCurrentlyHandledScreen(AbstractContainerScreen.class).orElse(null);
                    if (screen == null) return false;

                    for (Slot slot : screen.getMenu().slots) {
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
    public Result modifyLore(ItemStack stack, List<Component> lore, @Nullable Result previous) {
        if (!isRestricted) return Result.UNMODIFIED;

        int remaining = getClicksOverride() - getClicks();

        return withMerger(lore, merger -> {
            merger.copy(); // item name
            merger.add(Component.literal("Safety clicks remaining: " + remaining).withStyle(style -> style.withColor(ChatFormatting.YELLOW).withItalic(false)));
            merger.add(Component.literal(""));

            return Result.MODIFIED;
        });
    }

    @Override
    public Optional<DataComponentPatch> patchComponents(ItemStack stack) {
        if (!isRestricted) return Optional.empty();

        int remaining = getClicksOverride() - getClicks();

        return Optional.of(DataComponentPatch.builder()
                .set(CustomDataComponents.CUSTOM_SIZE, String.valueOf(remaining))
                .build());
    }

    //  internal mirroring itembutton
    private void retriggerModifier() {
        AbstractContainerScreen<?> screen = ScreenManager.getCurrentlyHandledScreen(AbstractContainerScreen.class).orElse(null);
        if (screen == null) return;

        state.map(RestrictionState::targetItem)
                .flatMap(info -> screen.getMenu().slots.stream()
                .filter(slot -> slot.getContainerSlot() == info.slotIndex())
                .findFirst())
                .ifPresent(slot -> {
                    ItemModifiers.clear(slot.getItem());
                    ItemModifiers.tryModify(slot.getItem(), ModifierSource.INVENTORY, slot);
                });
    }
}