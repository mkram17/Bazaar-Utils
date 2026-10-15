package com.github.mkram17.bazaarutils.utils;

import com.github.mkram17.bazaarutils.features.DisableErrorNotifications;
import com.github.mkram17.bazaarutils.generated.BazaarUtilsModules;
import com.github.mkram17.bazaarutils.testsupport.MinecraftTestSupport;
import net.minecraft.network.chat.Component;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import java.util.List;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class UtilErrorNotificationTest {
    private DisableErrorNotifications previousNotificationSetting;
    private DisableErrorNotifications notificationSetting;
    private Logger logger;
    private MockedStatic<LogManager> logging;
    private MockedStatic<PlayerActionUtil> delivery;
    private final Throwable cause = new IllegalStateException("Connection reset");

    @BeforeAll
    static void bootstrap() {
        MinecraftTestSupport.bootstrap();
    }

    @BeforeEach
    void setUp() {
        previousNotificationSetting = BazaarUtilsModules.DisableErrorNotifications;
        notificationSetting = mock(DisableErrorNotifications.class);
        BazaarUtilsModules.DisableErrorNotifications = notificationSetting;
        logger = mock(Logger.class);
        logging = mockStatic(LogManager.class);
        logging.when(() -> LogManager.getLogger(anyString())).thenReturn(logger);
        delivery = mockStatic(PlayerActionUtil.class);
    }

    @AfterEach
    void tearDown() {
        BazaarUtilsModules.DisableErrorNotifications = previousNotificationSetting;
        delivery.close();
        logging.close();
    }

    @Test
    void everyRepeatedErrorIsLoggedBeforeItsChatMessageIsCoalesced() {
        var messages = IntStream.rangeClosed(1, 20)
                .mapToObj(count -> "Fetch failure (failures=" + count + ")")
                .toList();
        for (String message : messages) Util.notifyError(message, cause);

        assertEquals(messages, loggedMessages(20));
        delivery.verify(() -> PlayerActionUtil.sendErrorMessage(
                eq(UtilErrorNotificationTest.class.getSimpleName()), any(Component.class)), times(20));
    }

    @Test
    void disablingChatNotificationsStillLogsEveryError() {
        when(notificationSetting.isEnabled()).thenReturn(true);
        Util.notifyError("First failure", cause);
        Util.notifyError("Second failure", cause);

        assertEquals(List.of("First failure", "Second failure"), loggedMessages(2));
        delivery.verifyNoInteractions();
    }

    @Test
    void failedChatDispatchStillLeavesTheOriginalErrorInTheLog() {
        delivery.when(() -> PlayerActionUtil.sendErrorMessage(anyString(), any(Component.class)))
                .thenThrow(new IllegalStateException("Client unavailable"));

        assertThrows(IllegalStateException.class, () -> Util.notifyError("Fetch failure", cause));
        assertEquals(List.of("Fetch failure"), loggedMessages(1));
    }

    private List<String> loggedMessages(int count) {
        var messages = ArgumentCaptor.forClass(String.class);
        verify(logger, times(count)).error(anyString(), eq("UtilErrorNotificationTest"),
                messages.capture(), eq(cause.getMessage()), anyString());
        return messages.getAllValues();
    }
}
