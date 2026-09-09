package com.zpkdxgames.plexoncore.scheduler;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BinaryOperator;

public final class BatchAccumulator<K, V> {
    private final AtomicReference<ConcurrentHashMap<K, V>> current = new AtomicReference<>(new ConcurrentHashMap<>());
    private final BinaryOperator<V> merger;

    public BatchAccumulator(BinaryOperator<V> merger) {
        this.merger = Objects.requireNonNull(merger);
    }

    public void add(K key, V value) {
        Objects.requireNonNull(key);
        Objects.requireNonNull(value);
        current.get().merge(key, value, merger);
    }

    public Map<K, V> drain() {
        ConcurrentHashMap<K, V> drained = current.getAndSet(new ConcurrentHashMap<>());
        return Map.copyOf(drained);
    }

    public int pendingKeys() {
        return current.get().size();
    }
}
