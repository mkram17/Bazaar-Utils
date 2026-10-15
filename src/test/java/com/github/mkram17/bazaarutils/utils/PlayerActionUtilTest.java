package com.github.mkram17.bazaarutils.utils;

import com.github.mkram17.bazaarutils.testsupport.MinecraftTestSupport;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.ArrayDeque;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PlayerActionUtilTest {
    private Minecraft client;
    private LocalPlayer player;
    private MockedStatic<Minecraft> minecraft;
    private MockedStatic<Util> utilities;
    private final ArrayDeque<Runnable> clientTasks = new ArrayDeque<>();
    private final ArrayDeque<Runnable> retryTasks = new ArrayDeque<>();

    @BeforeAll
    static void bootstrap() {
        MinecraftTestSupport.bootstrap();
    }

    @BeforeEach
    void setUp() {
        client = mock(Minecraft.class);
        player = mock(LocalPlayer.class);
        doAnswer(invocation -> {
            clientTasks.add(invocation.getArgument(0));
            return null;
        }).when(client).execute(any(Runnable.class));
        minecraft = mockStatic(Minecraft.class);
        minecraft.when(Minecraft::getInstance).thenReturn(client);
        utilities = mockStatic(Util.class);
        utilities.when(() -> Util.tickExecuteLater(anyInt(), any(Runnable.class)))
                .thenAnswer(invocation -> {
                    retryTasks.add(invocation.getArgument(1));
                    return null;
                });
    }

    @AfterEach
    void tearDown() {
        // Drain through the real delivery path so pending state cannot leak between tests.
        try {
            client.player = player;
            runClientTasks();
            while (!retryTasks.isEmpty()) retryTasks.removeFirst().run();
        } finally {
            utilities.close();
            minecraft.close();
        }
    }

    @Test
    void repeatedErrorsWithChangingCountsDeliverOnlyTheLatestMessage() {
        Component latest = null;
        for (int i = 1; i <= 20; i++) {
            latest = Component.literal("Fetch failure (failures=" + i + ")").withStyle(ChatFormatting.RED);
            PlayerActionUtil.sendErrorMessage("BazaarDataManager", latest);
        }
        runClientTasks();
        assertEquals(1, retryTasks.size());
        verifyNoInteractions(player);

        client.player = player;
        retryTasks.removeFirst().run();
        verify(player).sendSystemMessage(latest);
        verifyNoMoreInteractions(player);
        assertTrue(retryTasks.isEmpty());
    }

    @Test
    void separateErrorSourcesKeepTheirOwnPendingMessages() {
        var marketError = Component.literal("Market data unavailable");
        var configError = Component.literal("Config could not be saved");
        PlayerActionUtil.sendErrorMessage("BazaarDataManager", marketError);
        PlayerActionUtil.sendErrorMessage("ConfigUtil", configError);
        runClientTasks();
        assertEquals(1, retryTasks.size());

        client.player = player;
        retryTasks.removeFirst().run();
        verify(player).sendSystemMessage(marketError);
        verify(player).sendSystemMessage(configError);
        verifyNoMoreInteractions(player);
    }

    @Test
    void waitingForAPlayerKeepsOnlyOneScheduledRetry() {
        var message = Component.literal("Fetch failure");
        PlayerActionUtil.sendErrorMessage("BazaarDataManager", message);
        runClientTasks();
        for (int i = 0; i < 3; i++) {
            retryTasks.removeFirst().run();
            PlayerActionUtil.sendErrorMessage("BazaarDataManager", message);
            runClientTasks();
            assertEquals(1, retryTasks.size());
        }
        verifyNoInteractions(player);
        utilities.verify(() -> Util.tickExecuteLater(eq(100), any(Runnable.class)), times(4));

        client.player = player;
        retryTasks.removeFirst().run();
        verify(player).sendSystemMessage(message);
        verifyNoMoreInteractions(player);
    }

    @Test
    void deliveredErrorsDoNotSuppressErrorsDuringALaterDisconnect() {
        var first = Component.literal("First failure");
        PlayerActionUtil.sendErrorMessage("BazaarDataManager", first);
        runClientTasks();
        client.player = player;
        retryTasks.removeFirst().run();

        client.player = null;
        var second = Component.literal("Later failure");
        PlayerActionUtil.sendErrorMessage("BazaarDataManager", second);
        runClientTasks();
        assertEquals(1, retryTasks.size());
        client.player = player;
        retryTasks.removeFirst().run();

        verify(player).sendSystemMessage(first);
        verify(player).sendSystemMessage(second);
        verifyNoMoreInteractions(player);
    }

    @Test
    void anErrorDeliveredAfterJoiningReplacesItsOlderPendingMessage() {
        PlayerActionUtil.sendErrorMessage("BazaarDataManager", Component.literal("Old failure"));
        runClientTasks();
        client.player = player;
        var latest = Component.literal("Latest failure");
        PlayerActionUtil.sendErrorMessage("BazaarDataManager", latest);
        runClientTasks();
        retryTasks.removeFirst().run();

        verify(player).sendSystemMessage(latest);
        verifyNoMoreInteractions(player);
        assertTrue(retryTasks.isEmpty());
    }

    @Test
    void availablePlayerReceivesEachErrorOnTheClientThread() {
        client.player = player;
        var first = Component.literal("First failure");
        var second = Component.literal("Second failure");
        PlayerActionUtil.sendErrorMessage("BazaarDataManager", first);
        PlayerActionUtil.sendErrorMessage("BazaarDataManager", second);
        verifyNoInteractions(player);

        runClientTasks();
        verify(player).sendSystemMessage(first);
        verify(player).sendSystemMessage(second);
        verifyNoMoreInteractions(player);
        assertTrue(retryTasks.isEmpty());
    }

    private void runClientTasks() {
        while (!clientTasks.isEmpty()) clientTasks.removeFirst().run();
    }
}
