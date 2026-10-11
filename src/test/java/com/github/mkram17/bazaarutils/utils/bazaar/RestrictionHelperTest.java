package com.github.mkram17.bazaarutils.utils.bazaar;

import com.github.mkram17.bazaarutils.events.RegistrationScope;
import com.github.mkram17.bazaarutils.events.minecraft.ContainerLoadedEvent;
import com.github.mkram17.bazaarutils.events.minecraft.SlotInteractionEvent;
import com.github.mkram17.bazaarutils.features.gui.inventory.restrictions.controls.DoubleRestrictionControl;
import com.github.mkram17.bazaarutils.features.gui.inventory.restrictions.controls.NumericRestrictBy;
import com.github.mkram17.bazaarutils.features.gui.inventory.restrictions.controls.RestrictionControl;
import com.github.mkram17.bazaarutils.testsupport.MinecraftTestSupport;
import com.github.mkram17.bazaarutils.utils.PlayerActionUtil;
import com.github.mkram17.bazaarutils.utils.minecraft.ItemInfo;
import com.github.mkram17.bazaarutils.utils.minecraft.gui.ScreenManager;
import com.github.mkram17.bazaarutils.utils.minecraft.gui.ScreenMatcher;
import com.github.mkram17.bazaarutils.utils.bazaar.gui.BazaarScreenType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class RestrictionHelperTest {
    private static final int TARGET_SLOT = 11;

    @BeforeAll
    static void bootstrap() {
        MinecraftTestSupport.bootstrap();
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 3})
    void firstNInteractionsAreCanceledAndTheNextProceeds(int safetyClicks) {
        try (var environment = new ClientBoundaryMocks()) {
            var helper = new TestRestrictionHelper(safetyClicks, true);
            helper.onContainerLoaded(null);
            for (int i = 0; i < safetyClicks; i++) {
                var event = interaction(TARGET_SLOT, false);
                helper.onSlotClicked(event);
                verify(event).cancel();
                assertEquals(i + 1, helper.getClicks());
            }
            var next = interaction(TARGET_SLOT, false);
            helper.onSlotClicked(next);
            verify(next, never()).cancel();
            assertEquals(safetyClicks, helper.getClicks());
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void openingOrReloadingAScreenResetsTheCount(boolean reload) {
        try (var environment = new ClientBoundaryMocks()) {
            var helper = new TestRestrictionHelper(2, true);
            helper.onContainerLoaded(null);
            helper.onSlotClicked(interaction(TARGET_SLOT, false));
            assertEquals(1, helper.getClicks());
            if (reload) {
                helper.onContainerLoaded(null);
            } else {
                helper.onScreenInitialized(null);
            }
            assertEquals(0, helper.getClicks());
            if (!reload) helper.onContainerLoaded(null);
            var next = interaction(TARGET_SLOT, false);
            helper.onSlotClicked(next);
            verify(next).cancel();
            assertEquals(1, helper.getClicks());
        }
    }

    @ParameterizedTest
    @CsvSource({"12, false", "11, true"})
    void unrelatedContainerAndPlayerInventorySlotsAreNotBlocked(int slotIndex, boolean playerInventory) {
        try (var environment = new ClientBoundaryMocks()) {
            var helper = new TestRestrictionHelper(2, true);
            helper.onContainerLoaded(null);
            var event = interaction(slotIndex, playerInventory);
            helper.onSlotClicked(event);
            verify(event, never()).cancel();
            assertEquals(0, helper.getClicks());
        }
    }

    @Test
    void anUnrestrictedSaleDoesNotRequireSafetyClicks() {
        try (var environment = new ClientBoundaryMocks()) {
            var helper = new TestRestrictionHelper(2, false);
            helper.onContainerLoaded(null);
            var event = interaction(TARGET_SLOT, false);
            helper.onSlotClicked(event);
            verify(event, never()).cancel();
            assertEquals(0, helper.getClicks());
        }
    }

    private static SlotInteractionEvent interaction(int slotIndex, boolean playerInventory) {
        var slot = mock(Slot.class);
        when(slot.getContainerSlot()).thenReturn(slotIndex);
        var event = mock(SlotInteractionEvent.class);
        when(event.getSlot()).thenReturn(slot);
        when(event.isInPlayerInventory()).thenReturn(playerInventory);
        return event;
    }

    /** Replaces only registration, screen refresh and notifications; the handlers are real. */
    private static final class ClientBoundaryMocks implements AutoCloseable {
        private final MockedStatic<RegistrationScope> registration = Mockito.mockStatic(RegistrationScope.class);
        private final MockedStatic<PlayerActionUtil> notifications = Mockito.mockStatic(PlayerActionUtil.class);
        private final MockedStatic<ScreenManager> screens = Mockito.mockStatic(ScreenManager.class);

        private ClientBoundaryMocks() {
            screens.when(ScreenManager::getInstance).thenReturn(mock(ScreenManager.class));
        }

        @Override
        public void close() {
            screens.close();
            notifications.close();
            registration.close();
        }
    }

    private static final class TestRestrictionHelper extends RestrictionHelper<RestrictionHelper.RestrictionState> {
        private final int safetyClicks;
        private final boolean restricted;

        private TestRestrictionHelper(int safetyClicks, boolean restricted) {
            super("Test sell restrictions");
            this.safetyClicks = safetyClicks;
            this.restricted = restricted;
        }

        @Override
        protected int getClicksOverride() { return safetyClicks; }

        @Override
        protected String getMessagePrefix() { return "Sell protected:"; }

        @Override
        protected List<RestrictionControl<?>> getRestrictors() {
            return restricted ? List.of(new DoubleRestrictionControl(NumericRestrictBy.PRICE, 100)) : List.of();
        }

        @Override
        protected Optional<RestrictionState> makeState(ContainerLoadedEvent event) {
            return Optional.of(new RestrictionState() {
                @Override
                public ItemInfo targetItem() { return new ItemInfo(TARGET_SLOT, ItemStack.EMPTY); }

                @Override
                public List<RestrictionControl<?>> triggeredRestrictors() { return getRestrictors(); }
            });
        }

        @Override
        public boolean isEnabled() { return true; }

        @Override
        public ScreenMatcher<BazaarScreenType> screenConstraints() {
            return ScreenMatcher.any(BazaarScreenType.class);
        }
    }
}
