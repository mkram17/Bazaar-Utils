package com.github.mkram17.bazaarutils.utils;

import java.time.*;

import javax.swing.Timer;

import com.github.mkram17.bazaarutils.utils.annotations.autoregistration.RunOnInit;
import lombok.Getter;

public class TimeUtil {
    private static final BazaarLogger LOG = BazaarLogger.of(TimeUtil.class);

    @Getter
    private static ZonedDateTime modInitTime;
    public static final ZonedDateTime LAST_BAZAAR_LIMIT_RESET_TIME = ZonedDateTime.of(LocalDate.now(ZoneOffset.UTC), LocalTime.MIDNIGHT, ZoneOffset.UTC);
    @Getter
    private static ZonedDateTime nextBazaarLimitReset;
    @Getter
    private static long timeToBazaarLimitReset;

    private static void updateTime() {
        modInitTime = ZonedDateTime.now(ZoneOffset.UTC);
        nextBazaarLimitReset = modInitTime.toLocalDate().plusDays(1).atStartOfDay(ZoneOffset.UTC);
        timeToBazaarLimitReset = Duration.between(modInitTime, nextBazaarLimitReset).toSeconds();
    }

    @RunOnInit
    public static void startTimer() {
        updateTime();
        LOG.info("TimeUtil initialised — nextBazaarLimitReset={} ({}s)", nextBazaarLimitReset, timeToBazaarLimitReset);

        Timer timer = new Timer(10 * 1000, e -> {
            updateTime();
        });

        timer.setRepeats(true);
        timer.start();
    }
}
