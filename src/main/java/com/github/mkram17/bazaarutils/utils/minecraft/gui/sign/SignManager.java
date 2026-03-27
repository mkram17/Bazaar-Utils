package com.github.mkram17.bazaarutils.utils.minecraft.gui.sign;

import com.github.mkram17.bazaarutils.events.screen.SignOpenEvent;
import com.github.mkram17.bazaarutils.misc.NotificationType;
import com.github.mkram17.bazaarutils.mixin.AccessorSignEditScreen;
import com.github.mkram17.bazaarutils.utils.PlayerActionUtil;
import com.github.mkram17.bazaarutils.utils.Util;
import com.github.mkram17.bazaarutils.utils.minecraft.gui.ScreenContext;
import com.github.mkram17.bazaarutils.utils.minecraft.gui.ScreenManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.SignEditScreen;
import tech.thatgravyboat.skyblockapi.api.events.base.Subscription;

import java.util.Optional;
import java.util.function.Consumer;

import static com.github.mkram17.bazaarutils.BazaarUtils.EVENT_BUS;

public class SignManager {
    public static void runOnNextSignOpen(Consumer<SignOpenEvent> action) {
        EVENT_BUS.register(new Object() {
            @Subscription(priority = Subscription.HIGHEST)
            private void onSignOpen(SignOpenEvent event) {
                try {
                    action.accept(event);
                } finally {
                    EVENT_BUS.unregister(this);
                }
            }
        });
    }

    public static void closeSign() {
        try {
            PlayerActionUtil.notifyAll("Closing sign", NotificationType.GUI);

            Minecraft client = Minecraft.getInstance();
            ScreenManager screenManager = ScreenManager.getInstance();

            Optional<ScreenContext> context = screenManager.current();

            if (context.isEmpty()) {
                Util.notifyError("Error closing sign: client and manager was null or not in a sign", new Throwable());

                return;
            }

            if (client.screen instanceof SignEditScreen signEditScreen && signEditScreen != context.get().screen()) {
                screenManager.setCurrentScreen(signEditScreen);
            }

            client.execute(context.get().screen()::onClose);
        } catch (Exception e) {
            Util.notifyError("Unknown error while closing sign", e);
        }
    }

    public static void setSignText(String text, boolean closeAfter) {
        setSignTextInternal(text, closeAfter, 5);
    }

    private static void setSignTextInternal(String text, boolean closeAfter, int attemptsLeft) {
        if (attemptsLeft <= 0) {
            Util.notifyError("Failed to set Sign text: max amount of attempts reached.", new Throwable());

            return;
        }

        Minecraft client = Minecraft.getInstance();

        if (client == null) {
            Util.notifyError("Failed to set sign text: MinecraftClient is null.", new Throwable());

            return;
        }

        client.execute(() -> {
            ScreenManager screenManager = ScreenManager.getInstance();

            Screen currentScreen = client.screen;
            if (currentScreen instanceof SignEditScreen) {
                Optional<ScreenContext> context = screenManager.current();

                if (context.isEmpty() || context.get().screen() != currentScreen) {
                    screenManager.setCurrentScreen(currentScreen);
                }
            }

            Optional<ScreenContext> context = screenManager.current();

            if (context.isEmpty()) {
                Util.tickExecuteLater(4, () -> setSignTextInternal(text, closeAfter, attemptsLeft - 1));

                return;
            }

            Screen screen = context.get().screen();

            if (!(screen instanceof SignEditScreen)) {
                Util.tickExecuteLater(4, () -> setSignTextInternal(text, closeAfter, attemptsLeft - 1));

                return;
            }



            if (client.screen instanceof SignEditScreen signEditScreen && signEditScreen != context.get().screen()) {
                screenManager.setCurrentScreen(signEditScreen);
            }

            try {
                AccessorSignEditScreen signScreen = (AccessorSignEditScreen) context.get().screen();

                String[] lines = text.split("\n", 4);
                int originalRow = signScreen.getLine();

                for (int i = 0; i < 4; i++) {
                    String line = i < lines.length ? lines[i] : "";

                    signScreen.setLine(i);
                    signScreen.callSetMessage(line);
                }

                signScreen.setLine(originalRow);

                if (closeAfter) {
                    closeSign();
                }
            } catch (Exception exception) {
                Util.notifyError("Error executing sign text update", exception);

                exception.printStackTrace();
            }
        });
    }

}
