package com.zpkdxgames.plexoncore;

import com.zpkdxgames.plexoncore.event.PlexonEvents;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

class EventUtilitiesTest {
    @Test void tokenNormalizationIsStable() {
        String token = PlexonEvents.normalizeToken("Plexon Quests", " reward-42 ");
        assertEquals("plexon-quests:reward-42", token);
        assertEquals(PlexonEvents.hashToken(token), PlexonEvents.hashToken(token));
    }

    @Test void memoryDedupeSuppressesImmediateDuplicate() {
        PlexonEvents.MemoryDedupe dedupe = new PlexonEvents.MemoryDedupe(Duration.ofMinutes(1));
        assertTrue(dedupe.first("quests:abc"));
        assertFalse(dedupe.first("quests:abc"));
        assertTrue(dedupe.first("quests:def"));
    }
}
