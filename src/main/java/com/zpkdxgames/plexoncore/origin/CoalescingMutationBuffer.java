package com.zpkdxgames.plexoncore.origin;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Lock-free producer / single-flush coalescing buffer. A key has at most one authoritative pending
 * mutation and later writes replace earlier writes before persistence. Queue entries are only used
 * to preserve bounded drain work; the map remains the source of truth.
 */
final class CoalescingMutationBuffer<K> {
    enum State { PRESENT, ABSENT }

    record Mutation<K>(K key, State state, long sequence) {}
    record Batch<K>(List<Mutation<K>> mutations) {
        Batch { mutations = List.copyOf(mutations); }
        boolean empty() { return mutations.isEmpty(); }
        int size() { return mutations.size(); }
    }

    private final ConcurrentHashMap<K, Mutation<K>> pending = new ConcurrentHashMap<>();
    private final ConcurrentLinkedQueue<K> order = new ConcurrentLinkedQueue<>();
    private final AtomicLong sequence = new AtomicLong();

    Mutation<K> put(K key, State state) {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(state, "state");
        Mutation<K> next = new Mutation<>(key, state, sequence.incrementAndGet());
        pending.compute(key, (ignored, previous) -> {
            if (previous == null) order.offer(key);
            return next;
        });
        return next;
    }

    Batch<K> drain(int maximum) {
        int limit = Math.max(1, maximum);
        Map<K, Mutation<K>> selected = new LinkedHashMap<>();
        while (selected.size() < limit) {
            K key = order.poll();
            if (key == null) break;
            Mutation<K> current = pending.get(key);
            if (current != null) selected.put(key, current);
        }
        return new Batch<>(new ArrayList<>(selected.values()));
    }

    /**
     * Removes mutations that were durably written. If a producer replaced a mutation while the
     * batch was in flight, that newer value is retained and requeued.
     */
    void complete(Batch<K> batch) {
        for (Mutation<K> mutation : batch.mutations()) {
            if (!pending.remove(mutation.key(), mutation) && pending.containsKey(mutation.key())) {
                order.offer(mutation.key());
            }
        }
    }

    /** Requeues a failed batch without overwriting any newer producer state. */
    void retry(Batch<K> batch) {
        for (Mutation<K> mutation : batch.mutations()) {
            if (pending.containsKey(mutation.key())) order.offer(mutation.key());
        }
    }

    int pendingSize() { return pending.size(); }
    boolean isEmpty() { return pending.isEmpty(); }
    void clear() { pending.clear(); order.clear(); }
}
