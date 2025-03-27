package io.cockroachdb.training.util;

import java.lang.reflect.UndeclaredThrowableException;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.Callable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.format.annotation.DurationFormat;
import org.springframework.format.datetime.standard.DurationFormatterUtils;

public abstract class Timer {
    private static final Logger logger = LoggerFactory.getLogger(Timer.class);

    private Timer() {
    }

    public static String durationToString(Duration duration) {
        return DurationFormatterUtils.print(duration, DurationFormat.Style.COMPOSITE);
    }

    public static void timedExecution(String label, Runnable task) {
        final Instant start = Instant.now();
        try {
            logger.debug("Processing {}", label);
            task.run();
        } finally {
            logger.debug("{} completed in {}", label, durationToString(Duration.between(start, Instant.now())));
        }
    }

    public static <V> V timedExecution(String label, Callable<V> task) {
        final Instant start = Instant.now();
        try {
            logger.debug("Processing {}", label);
            return task.call();
        } catch (Exception e) {
            throw new UndeclaredThrowableException(e);
        } finally {
            logger.debug("{} completed in {}", label, durationToString(Duration.between(start, Instant.now())));
        }
    }
}
