package com.zpkdxgames.plexoncore.event;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CoreRuntimeMetricsTest {
    @Test void reportsRollingLatencyPercentiles() {
        CoreRuntimeMetrics metrics = new CoreRuntimeMetrics();
        for (long value = 1; value <= 100; value++) metrics.gatewayNanos(value);
        var latency = metrics.snapshot().gateway();
        assertEquals(100, latency.samples());
        assertEquals(50, latency.p50Nanos());
        assertEquals(95, latency.p95Nanos());
        assertEquals(99, latency.p99Nanos());
        assertEquals(100, latency.maxNanos());
    }
}
