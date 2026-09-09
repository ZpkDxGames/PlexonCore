package com.zpkdxgames.plexoncore.event;

import java.util.Arrays;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicLongArray;
import java.util.concurrent.atomic.LongAdder;

public final class CoreRuntimeMetrics {
    private static final int LATENCY_WINDOW = 2048;
    private final LongAdder blockBreakReceived = new LongAdder();
    private final LongAdder blockBreakRouted = new LongAdder();
    private final LongAdder contextsCreated = new LongAdder();
    private final LongAdder itemIdentityInspections = new LongAdder();
    private final LongAdder pdcReads = new LongAdder();
    private final LongAdder originLookups = new LongAdder();
    private final LongAdder moduleFailures = new LongAdder();
    private final LatencyWindow gatewayLatency = new LatencyWindow(LATENCY_WINDOW);
    private final LatencyWindow contextLatency = new LatencyWindow(LATENCY_WINDOW);
    private final LatencyWindow dispatchLatency = new LatencyWindow(LATENCY_WINDOW);

    public void blockBreakReceived() { blockBreakReceived.increment(); }
    public void blockBreakRouted() { blockBreakRouted.increment(); }
    public void contextCreated() { contextsCreated.increment(); }
    public void itemIdentityInspection() { itemIdentityInspections.increment(); }
    public void pdcRead() { pdcReads.increment(); }
    public void originLookup() { originLookups.increment(); }
    public void moduleFailure() { moduleFailures.increment(); }
    public void gatewayNanos(long nanos) { gatewayLatency.record(nanos); }
    public void contextNanos(long nanos) { contextLatency.record(nanos); }
    public void dispatchNanos(long nanos) { dispatchLatency.record(nanos); }

    public Snapshot snapshot() {
        return new Snapshot(
            blockBreakReceived.sum(), blockBreakRouted.sum(), contextsCreated.sum(), itemIdentityInspections.sum(),
            pdcReads.sum(), originLookups.sum(), moduleFailures.sum(), gatewayLatency.snapshot(), contextLatency.snapshot(), dispatchLatency.snapshot()
        );
    }

    public record Snapshot(long blockBreakReceived, long blockBreakRouted, long contextsCreated,
                           long itemIdentityInspections, long pdcReads, long originLookups, long moduleFailures,
                           LatencySnapshot gateway, LatencySnapshot contextBuild, LatencySnapshot dispatch) {}

    public record LatencySnapshot(long samples, long p50Nanos, long p95Nanos, long p99Nanos, long maxNanos) {}

    private static final class LatencyWindow {
        private final AtomicLongArray values;
        private final AtomicLong cursor = new AtomicLong();

        private LatencyWindow(int size) {
            values = new AtomicLongArray(size);
        }

        void record(long nanos) {
            long index = cursor.getAndIncrement();
            values.set((int) (index % values.length()), Math.max(0L, nanos));
        }

        LatencySnapshot snapshot() {
            long total = Math.min(cursor.get(), values.length());
            if (total <= 0) return new LatencySnapshot(0, 0, 0, 0, 0);
            long[] copy = new long[(int) total];
            for (int i = 0; i < copy.length; i++) copy[i] = values.get(i);
            Arrays.sort(copy);
            return new LatencySnapshot(total, percentile(copy, 0.50), percentile(copy, 0.95), percentile(copy, 0.99), copy[copy.length - 1]);
        }

        private static long percentile(long[] sorted, double percentile) {
            int index = (int) Math.ceil(percentile * sorted.length) - 1;
            return sorted[Math.max(0, Math.min(sorted.length - 1, index))];
        }
    }
}
