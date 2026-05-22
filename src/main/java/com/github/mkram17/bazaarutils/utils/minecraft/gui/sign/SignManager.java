package com.github.mkram17.bazaarutils.utils.minecraft.gui.sign;

import com.github.mkram17.bazaarutils.BazaarUtils;
import com.github.mkram17.bazaarutils.events.minecraft.SignOpenEvent;
import com.github.mkram17.bazaarutils.misc.NotificationType;
import com.github.mkram17.bazaarutils.mixin.AccessorSignEditScreen;
import com.github.mkram17.bazaarutils.utils.PlayerActionUtil;
import com.github.mkram17.bazaarutils.utils.Priority;
import com.github.mkram17.bazaarutils.utils.Result;
import com.github.mkram17.bazaarutils.utils.Util;
import com.github.mkram17.bazaarutils.utils.minecraft.gui.ScreenContext;
import com.github.mkram17.bazaarutils.utils.minecraft.gui.ScreenManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.SignEditScreen;
import tech.thatgravyboat.skyblockapi.api.events.base.Subscription;
import tech.thatgravyboat.skyblockapi.api.events.screen.ContainerCloseEvent;

import java.util.Optional;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.Function;

public class SignManager {
    /** Handlers queued for the next sign open; drained by {@link SignQueueDispatcher} in order. */
    private static final Queue<Function<SignOpenEvent, Result>> PENDING = new ConcurrentLinkedQueue<>();

    static {
        BazaarUtils.EVENT_BUS.register(new SignQueueDispatcher());
    }

    private static final class SignQueueDispatcher {
        @Subscription(priority = Priority.FIRST)
        private void onSignOpen(SignOpenEvent event) {
            Function<SignOpenEvent, Result> handler;

            while ((handler = PENDING.poll()) != null) {
                Result result = handler.apply(event);

                if (!result.propagate()) break;
            }
        }

        @Subscription(priority = Priority.FIRST)
        private void onContainerClosed(ContainerCloseEvent event) {
            PENDING.clear();
        }
    }

    /**
     * Queues a handler for the next sign that opens. Handlers run in order against a single
     * {@link SignOpenEvent}; return {@link Result#CONSUMED} once you've claimed the sign so
     * nothing else queued fires on it. The whole queue clears on {@link ContainerCloseEvent},
     * so a handler waiting on a sign that never opens doesn't fire on some later, unrelated one.
     */
    public static void runOnNextSignOpen(Function<SignOpenEvent, Result> handler) {
        PENDING.add(handler);
    }

    public static void setSignText(String text, boolean closeAfter) {
        setSignTextInternal(text, closeAfter, 5);
    }


    private static void setSignTextInternal(String text, boolean closeAfter, int attemptsLeft) {
        if (attemptsLeft <= 0) {
            Util.notifyError("Failed to set sign text: max attempts reached.", new Throwable());
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
                Util.notifyError("Error executing sign text update", exception);
            }
        });
    }

    public static void closeSign() {
        try {
            PlayerActionUtil.notifyAll("Closing sign", NotificationType.GUI);

            syncSignScreen();

            ScreenManager.getInstance().current()
                    .map(ScreenContext::screen)
                    .ifPresentOrElse(
                            screen -> Minecraft.getInstance().execute(screen::onClose),
                            () -> Util.notifyError("Error closing sign: not in a sign screen", new Throwable())
                    );
        } catch (Exception e) {
            Util.notifyError("Unknown error while closing sign", e);
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
