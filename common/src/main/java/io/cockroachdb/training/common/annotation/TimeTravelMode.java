package io.cockroachdb.training.common.annotation;

/**
 * Enumeration of time-travel query modes.
 * <p>
 * See {@link <a href="https://www.cockroachlabs.com/docs/stable/as-of-system-time.html">AS OF SYSTEM TIME</a>}
 *
 * @author Kai Niemi
 */
public enum TimeTravelMode {
    /**
     * Non-authoritative read from the closest range replica.
     */
    FOLLOWER_READ,
    /**
     * Non-authoritative read using a relative timestamp.
     */
    HISTORICAL_READ,
    /**
     * Authoritative reads (default in CockroachDB)
     */
    DISABLED
}
