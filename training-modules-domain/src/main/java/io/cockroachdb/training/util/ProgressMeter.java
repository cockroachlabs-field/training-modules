package io.cockroachdb.training.util;

import java.time.Duration;
import java.time.Instant;

public class ProgressMeter {
    private long current;

    private long total;

    private Instant startTime;

    private String label;

    public long getCurrent() {
        return current;
    }

    public ProgressMeter setCurrent(long current) {
        this.current = current;
        return this;
    }

    public long getTotal() {
        return total;
    }

    public ProgressMeter setTotal(long total) {
        this.total = total;
        return this;
    }

    public ProgressMeter setStartTime(Instant startTime) {
        this.startTime = startTime;
        return this;
    }

    public ProgressMeter setLabel(String label) {
        this.label = label;
        return this;
    }

    public String getLabel() {
        return label;
    }

    public Duration getElapsedTime() {
        return Duration.between(startTime, Instant.now());
    }

    public double getCallsPerSec() {
        return (double) current / Math.max(1, getElapsedTime().toMillis()) * 1000.0;
    }

    public long getRemainingMillis() {
        double cps = getCallsPerSec();
        return cps > 0 ? (long) ((total - current) / cps * 1000) : 0;
    }

    public void printProgressBar() {
        AsciiArt.printProgressBar(
                getTotal(),
                getCurrent(),
                getLabel(),
                getCallsPerSec(),
                getRemainingMillis());
    }
}
