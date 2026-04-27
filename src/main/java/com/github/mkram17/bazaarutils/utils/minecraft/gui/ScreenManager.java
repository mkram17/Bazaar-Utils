package com.github.mkram17.bazaarutils.utils.minecraft.gui;

import com.github.mkram17.bazaarutils.events.ContainerLoadedEvent;
import com.github.mkram17.bazaarutils.events.ScreenChangeEvent;
import com.github.mkram17.bazaarutils.misc.NotificationType;
import com.github.mkram17.bazaarutils.utils.PlayerActionUtil;
import com.github.mkram17.bazaarutils.utils.Util;
import com.github.mkram17.bazaarutils.utils.annotations.autoregistration.RunOnInit;
import com.github.mkram17.bazaarutils.utils.bazaar.gui.BazaarScreenType;
import com.github.mkram17.bazaarutils.utils.minecraft.gui.container.ContainerManager;
import lombok.Getter;
import meteordevelopment.orbit.EventHandler;
import meteordevelopment.orbit.EventPriority;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.SignEditScreen;
import net.minecraft.network.protocol.game.ServerboundContainerClosePacket;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.AbstractContainerMenu;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import static com.github.mkram17.bazaarutils.BazaarUtils.EVENT_BUS;

public class ScreenManager {
    @Getter
    private static final ScreenManager instance = new ScreenManager();

    @RunOnInit
    public static void initialize() {
        BazaarScreenType.registerAll();

        EVENT_BUS.subscribe(ScreenManager.class);

        ScreenEvents.AFTER_INIT.register((client, screen, width, height) -> instance.setCurrentScreen(screen));
    }

    private static final Set<ScreenType> types = ConcurrentHashMap.newKeySet();

    public static void register(ScreenType type) {
        if (!types.add(type)) {
            Util.notifyError("ScreenType registered twice: " + type, new Throwable());
        }
    }

    public static Optional<ScreenType> matchType(Screen screen) {
        for (ScreenType type : types) {
            try {
                if (type.test(screen)) return Optional.of(type);
            } catch (Exception ignored) {
            }
        }
        return Optional.empty();
    }

    public record ScreenSnapshot(Screen screen, ScreenType type) {}

    private static final int MAX_HISTORY = 8;

    private final ArrayDeque<ScreenSnapshot> history = new ArrayDeque<>(MAX_HISTORY);

    /**
     * True immediately after a screen closes that is known to cause the server to
     * open a follow-up screen, leaving Minecraft momentarily with no current screen
     * between the two (so the follow-up arrives with prev=null).
     * <p>
     * This distinguishes two structurally identical events:
     *   (a) prev=null, next=Screen after a follow-up close → history is valid, keep it
     *   (b) prev=null, next=Screen from the game world     → history is stale, clear it
     * Reset as soon as the follow-up screen is pushed.
     */
    private boolean expectingServerFollowUp = false;

    @EventHandler(priority = EventPriority.HIGHEST)
    private static void onScreenChange(ScreenChangeEvent event) {
        Screen next = event.getNewScreen();
        Screen prev = event.getOldScreen();

        if (next == null) {
            if (prev == null) return;

            // A screen closed. We check whether it is a known overlay which double nulls currentScreen
            instance.expectingServerFollowUp = isFollowUpScreen(prev);
            instance.logHistory("CLOSE");

            return;
        }

        // A real screen is arriving. prev=null means nothing was open before it — either
        // we're starting fresh from the game world, or a server follow-up just arrived.
        if (prev == null && !instance.expectingServerFollowUp && !instance.history.isEmpty()) {
            instance.history.clear();
            instance.logHistory("CLEAR");
        }
        instance.expectingServerFollowUp = false;

        instance.setCurrentScreen(next);
    }

    /**
     * Returns true for screens that are known to cause the server to immediately open
     * a follow-up screen after they close, resulting in a prev=null arrival.
     */
    private static boolean isFollowUpScreen(Screen screen) {
        return screen instanceof SignEditScreen;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    private static void onChestLoaded(ContainerLoadedEvent event) {
        ContainerManager.onChestLoaded(event);

        AbstractContainerScreen<ChestMenu> screen = event.getScreen();
        ScreenType resolved = event.getType().orElse(null);

        List<ScreenSnapshot> list = instance.getHistorySnapshot();

        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).screen() == screen) {
                list.set(i, new ScreenSnapshot(screen, resolved));
                instance.history.clear();
                instance.history.addAll(list);
                instance.logHistory("LOADED");
                return;
            }
        }
    }

    public void setCurrentScreen(Screen screen) {
        if (screen == null) return;

        ScreenSnapshot snapshot = new ScreenSnapshot(screen, matchType(screen).orElse(null));

        ScreenSnapshot head = history.peekFirst();
        // ScreenEvents.AFTER_INIT fires after setScreen — same screen instance arriving twice is a no-op
        // we no longer check for a RETYPE op as the cases were we fall to that are generally ones where
        // we depend on off ContainerQuery, and that solely is handled by #onChestLoaded
        if (head != null && head.screen() == screen) return;

        if (history.size() >= MAX_HISTORY) history.removeLast();
        history.addFirst(snapshot);
        logHistory("PUSH");
    }

    private void logHistory(String trigger) {
        if (!NotificationType.GUI.isEnabled()) return;

        StringJoiner breadcrumb = new StringJoiner(" › ");

        for (ScreenSnapshot snap : history) {
            breadcrumb.add(snap.type() != null ? snap.type().name() : "???");
        }

        PlayerActionUtil.notifyAll("[" + trigger.strip() + "] " + breadcrumb, NotificationType.GUI);
    }

    public Optional<ScreenContext> current() {
        return Optional.ofNullable(history.peekFirst()).map(ScreenContext::new);
    }

    public Optional<ScreenContext> getAtDepth(int depth) {
        if (depth < 0 || depth >= history.size()) return Optional.empty();

        Iterator<ScreenSnapshot> it = history.iterator();
        ScreenSnapshot target = null;

        for (int i = 0; i <= depth; i++) {
            if (!it.hasNext()) return Optional.empty();
            target = it.next();
        }

        return Optional.ofNullable(target).map(ScreenContext::new);
    }

    public Optional<ScreenContext> previous() {
        return getAtDepth(1);
    }
    
    public Optional<ScreenContext> findBack(ScreenType... wanted) {
        Iterator<ScreenSnapshot> it = history.iterator();

        if (it.hasNext()) it.next();

        while (it.hasNext()) {
            ScreenSnapshot snap = it.next();
            if (snap.type() != null) {
                for (ScreenType w : wanted) {
                    if (snap.type() == w) return Optional.of(new ScreenContext(snap));
                }
            }
        }

        return Optional.empty();
    }

    public List<ScreenSnapshot> getHistorySnapshot() {
        return new ArrayList<>(history);
    }

    public boolean isCurrent(ScreenType... wanted) {
        return current().map(ctx -> ctx.isAnyOf(wanted)).orElse(false);
    }

    public static <T extends AbstractContainerMenu> Optional<T> getMenu(Class<T> type) {
        Minecraft client = Minecraft.getInstance();

        if (client == null || client.player == null) {
            return Optional.empty();
        }

        return type.isInstance(client.player.containerMenu)
                ? Optional.of(type.cast(client.player.containerMenu))
                : Optional.empty();
    }

    public static <T extends Screen> Optional<T> getScreen(Class<T> type) {
        Minecraft client = Minecraft.getInstance();

        return type.isInstance(client.screen)
                ? Optional.of(type.cast(client.screen))
                : Optional.empty();
    }

    public static void closeScreen() {
        PlayerActionUtil.notifyAll("Closing GUI", NotificationType.GUI);

        if (getScreen(AbstractContainerScreen.class).isEmpty()) {
            Util.notifyError("Current screen does not implement HandledScreen", new Throwable());

            return;
        }

        try {
            Minecraft client = Minecraft.getInstance();

            client.execute(ScreenManager::doCloseScreen);
        } catch (Exception exception) {
            Util.notifyError("Error closing GUI", exception);
        }
    }

    private static void doCloseScreen() {
        try {
            Minecraft client = Minecraft.getInstance();

            if (client.player == null) {
                Util.notifyError("Player is null, cannot close screen", new Throwable());
                return;
            }

            client.player.connection.send(new ServerboundContainerClosePacket(client.player.containerMenu.containerId));
            client.setScreen(null);
            client.player.containerMenu = client.player.inventoryMenu;
        } catch (Exception exception) {
            Util.notifyError("Error encountered while closing screen with custom method", exception);
            throw new RuntimeException(exception);
        }
    }
}