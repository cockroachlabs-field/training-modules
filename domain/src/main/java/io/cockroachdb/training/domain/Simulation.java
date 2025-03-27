package io.cockroachdb.training.domain;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.format.annotation.DurationFormat;
import org.springframework.format.datetime.standard.DurationFormatterUtils;

import jakarta.persistence.LockModeType;

/**
 * Various simulation properties for the sake of demonstrating transaction semantics.
 */
public class Simulation {
    private final Logger logger = LoggerFactory.getLogger(getClass());

    public static Simulation none() {
        return new Simulation();
    }

    public static Simulation readModifyWrite() {
        return new Simulation().setPattern(Pattern.READ_MODIFY_WRITE);
    }

    public static Simulation writeOnly() {
        return new Simulation().setPattern(Pattern.WRITE_ONLY);
    }

    public enum Pattern {
        READ_MODIFY_WRITE,
        WRITE_ONLY
    }

    private Duration commitDelay = Duration.ofMillis(0);

    private Pattern pattern = Pattern.READ_MODIFY_WRITE;

    private LockModeType lockModeType = LockModeType.NONE;

    public Duration getCommitDelay() {
        return commitDelay;
    }

    public Simulation setCommitDelay(Duration commitDelay) {
        this.commitDelay = commitDelay;
        return this;
    }

    public LockModeType getLockModeType() {
        return lockModeType;
    }

    public Simulation setLockModeType(LockModeType lockModeType) {
        this.lockModeType = lockModeType;
        return this;
    }

    public Pattern getPattern() {
        return pattern;
    }

    public Simulation setPattern(Pattern pattern) {
        this.pattern = pattern;
        return this;
    }

    public void thinkTime() {
        // Use a fake pause to allow controlling contending updates with retries
        if (getCommitDelay().isPositive()) {
            try {
                logger.warn("Entering {} wait",
                        DurationFormatterUtils.print(getCommitDelay(), DurationFormat.Style.SIMPLE));
                TimeUnit.MILLISECONDS.sleep(getCommitDelay().toMillis());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                logger.warn("Proceeding after {} wait",
                        DurationFormatterUtils.print(getCommitDelay(), DurationFormat.Style.SIMPLE));
            }
        }
    }
}
