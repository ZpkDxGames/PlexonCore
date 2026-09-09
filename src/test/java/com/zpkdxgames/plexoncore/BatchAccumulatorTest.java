package com.zpkdxgames.plexoncore;

import com.zpkdxgames.plexoncore.scheduler.BatchAccumulator;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BatchAccumulatorTest {
    @Test void coalescesAndAtomicallyDrains() {
        BatchAccumulator<String, Integer> accumulator = new BatchAccumulator<>(Integer::sum);
        accumulator.add("stone", 2);
        accumulator.add("stone", 3);
        accumulator.add("ore", 1);
        assertEquals(2, accumulator.pendingKeys());
        assertEquals(Map.of("stone", 5, "ore", 1), accumulator.drain());
        assertTrue(accumulator.drain().isEmpty());
    }
}
