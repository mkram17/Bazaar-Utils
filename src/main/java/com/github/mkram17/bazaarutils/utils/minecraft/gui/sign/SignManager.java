package com.github.mkram17.bazaarutils.utils.minecraft.gui.sign;

import com.github.mkram17.bazaarutils.BazaarUtils;
import com.github.mkram17.bazaarutils.events.minecraft.SignOpenEvent;
import com.github.mkram17.bazaarutils.misc.NotificationType;
import com.github.mkram17.bazaarutils.mixin.AccessorSignEditScreen;
import com.github.mkram17.bazaarutils.utils.PlayerActionUtil;
import com.github.mkram17.bazaarutils.utils.Util;
import com.github.mkram17.bazaarutils.utils.minecraft.gui.ScreenContext;
import com.github.mkram17.bazaarutils.utils.minecraft.gui.ScreenManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.SignEditScreen;
import tech.thatgravyboat.skyblockapi.api.events.base.Subscription;

import java.util.Optional;
import java.util.function.Consumer;

public class SignManager {
    public static void runOnNextSignOpen(Consumer<SignOpenEvent> action) {
        BazaarUtils.EVENT_BUS.register(new Object() {
            @Subscription(priority = Integer.MIN_VALUE)
            private void onSignOpen(SignOpenEvent event) {
                try {
                    action.accept(event);
                } finally {
                    BazaarUtils.EVENT_BUS.unregister(this);
                }
            }
        });
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
