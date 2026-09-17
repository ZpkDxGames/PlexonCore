package com.zpkdxgames.plexoncore.event;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CoreEventGatewayFailureRateLimitTest {
    @Test
    void suppressedFailureDoesNotAdvanceLastEmittedTimestamp() {
        Map<String, Long> timestamps = new HashMap<>();
        long interval = 100L;

        assertTrue(CoreEventGateway.shouldEmitFailure(timestamps, "skills", 1_000L, interval));
        assertEquals(1_000L, timestamps.get("skills"));

        assertFalse(CoreEventGateway.shouldEmitFailure(timestamps, "skills", 1_050L, interval));
        assertEquals(1_000L, timestamps.get("skills"));

        assertTrue(CoreEventGateway.shouldEmitFailure(timestamps, "skills", 1_100L, interval));
        assertEquals(1_100L, timestamps.get("skills"));
    }

    @Test
    void subscribersHaveIndependentFailureWindows() {
        Map<String, Long> timestamps = new HashMap<>();
        assertTrue(CoreEventGateway.shouldEmitFailure(timestamps, "skills", 10L, 100L));
        assertTrue(CoreEventGateway.shouldEmitFailure(timestamps, "jobs", 20L, 100L));
        assertFalse(CoreEventGateway.shouldEmitFailure(timestamps, "skills", 30L, 100L));
        assertEquals(10L, timestamps.get("skills"));
        assertEquals(20L, timestamps.get("jobs"));
    }
}
