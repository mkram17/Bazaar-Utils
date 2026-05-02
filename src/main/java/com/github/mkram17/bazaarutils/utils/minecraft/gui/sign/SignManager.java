package com.github.mkram17.bazaarutils.utils.minecraft.gui.sign;

import com.github.mkram17.bazaarutils.BazaarUtils;
import com.github.mkram17.bazaarutils.events.minecraft.SignOpenEvent;
import com.github.mkram17.bazaarutils.misc.NotificationType;
import com.github.mkram17.bazaarutils.mixin.AccessorSignEditScreen;
import com.github.mkram17.bazaarutils.utils.BazaarLogger;
import com.github.mkram17.bazaarutils.utils.PlayerLogger;
import com.github.mkram17.bazaarutils.utils.Priority;
import com.github.mkram17.bazaarutils.utils.Util;
import com.github.mkram17.bazaarutils.utils.minecraft.gui.ScreenContext;
import com.github.mkram17.bazaarutils.utils.minecraft.gui.ScreenManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.SignEditScreen;
import tech.thatgravyboat.skyblockapi.api.events.base.Subscription;
import tech.thatgravyboat.skyblockapi.api.events.screen.ContainerCloseEvent;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

public class SignManager {
    private static final BazaarLogger LOG = BazaarLogger.of(SignManager.class);

    private static final AtomicBoolean listenerPending = new AtomicBoolean(false);

    public static void runOnNextSignOpen(Consumer<SignOpenEvent> action) {
        if (!listenerPending.compareAndSet(false, true)) return;

        Object listener = new Object() {
            @Subscription(priority = Priority.FIRST)
            private void onSignOpen(SignOpenEvent event) {
                try {
                    action.accept(event);
                } finally {
                    listenerPending.set(false);
                    BazaarUtils.EVENT_BUS.unregister(this);
                }
            }

            @Subscription(priority = Priority.FIRST)
            private void onContainerClosed(ContainerCloseEvent event) {
                listenerPending.set(false);
                BazaarUtils.EVENT_BUS.unregister(this);
            }
        };

        BazaarUtils.EVENT_BUS.register(listener);
    }

    public static void setSignText(String text, boolean closeAfter) {
        setSignTextInternal(text, closeAfter, 5);
    }

    private static void setSignTextInternal(String text, boolean closeAfter, int attemptsLeft) {
        if (attemptsLeft <= 0) {
            LOG.warn("Failed to set sign text: max attempts reached.", new Throwable());

            return;
        }

        Minecraft.getInstance().execute(() -> {
            syncSignScreen();

            Optional<ScreenContext> context = ScreenManager.getInstance().current();

            if (context.isEmpty() || !(context.get().screen() instanceof SignEditScreen)) {
                Util.tickExecuteLater(4, () -> setSignTextInternal(text, closeAfter, attemptsLeft - 1));

                return;
            }

            try {
                AccessorSignEditScreen signScreen = (AccessorSignEditScreen) context.get().screen();
                String[] lines = text.split("\n", 4);

                int originalRow = signScreen.getLine();

                for (int i = 0; i < 4; i++) {
                    signScreen.setLine(i);
                    signScreen.callSetMessage(i < lines.length ? lines[i] : "");
                }

                signScreen.setLine(originalRow);

                if (closeAfter) closeSign();
            } catch (Exception exception) {
                LOG.warn("Error executing sign text update", exception);
            }
        });
    }

    public static void closeSign() {
        try {
            PlayerLogger.debug("Closing sign", NotificationType.GUI, LOG);

            syncSignScreen();

            ScreenManager.getInstance().current()
                    .map(ScreenContext::screen)
                    .ifPresentOrElse(
                            screen -> Minecraft.getInstance().execute(screen::onClose),
                            () -> PlayerLogger.sendError("Error closing sign: not in a sign screen", new Throwable())
                    );
        } catch (Exception exception) {
            PlayerLogger.sendError("Unknown error while closing sign", exception);
        }
    }

    private static void syncSignScreen() {
        ScreenManager.getScreen(SignEditScreen.class).ifPresent(live -> {
            boolean outOfSync = ScreenManager.getInstance().current()
                    .map(context -> context.screen() != live)
                    .orElse(true);

            if (outOfSync) ScreenManager.getInstance().setCurrentScreen(live);
        });
    }
}
