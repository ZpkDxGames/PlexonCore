package com.zpkdxgames.plexoncore.event;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MemoryDedupeBoundedTest {
    @Test
    void cacheRejectsDuplicateAndNeverExceedsConfiguredMaximum() {
        PlexonEvents.MemoryDedupe dedupe = new PlexonEvents.MemoryDedupe(Duration.ofMinutes(1), 16);
        assertTrue(dedupe.first("same"));
        assertFalse(dedupe.first("same"));
        for (int i = 0; i < 64; i++) assertTrue(dedupe.first("token-" + i));
        assertTrue(dedupe.size() <= 16);
    }

    @Test
    void tokenBecomesEligibleAgainAfterTtl() throws Exception {
        PlexonEvents.MemoryDedupe dedupe = new PlexonEvents.MemoryDedupe(Duration.ofSeconds(1), 16);
        assertTrue(dedupe.first("expires"));
        assertFalse(dedupe.first("expires"));
        Thread.sleep(1_100L);
        assertTrue(dedupe.first("expires"));
    }
}
