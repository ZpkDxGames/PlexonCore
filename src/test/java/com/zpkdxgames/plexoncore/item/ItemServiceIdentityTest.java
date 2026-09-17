package com.zpkdxgames.plexoncore.item;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ItemServiceIdentityTest {
    @Test
    void oneIdentityResolvesRegardlessOfCandidateOrdering() {
        var first = ItemService.resolveIdentityCandidates(List.of(
                new ItemService.IdentityCandidate(2, "plexon", "item_id", "legendary_pickaxe"),
                new ItemService.IdentityCandidate(0, "plexon", "id", "legendary_pickaxe")));
        var second = ItemService.resolveIdentityCandidates(List.of(
                new ItemService.IdentityCandidate(0, "plexon", "id", "legendary_pickaxe"),
                new ItemService.IdentityCandidate(2, "plexon", "item_id", "legendary_pickaxe")));

        assertEquals(ItemService.IdentityStatus.RESOLVED, first.status());
        assertEquals(first.id(), second.id());
        assertEquals("plexon:legendary_pickaxe", first.id().orElseThrow());
    }

    @Test
    void conflictingAuthoritativeIdsFailClosed() {
        var identity = ItemService.resolveIdentityCandidates(List.of(
                new ItemService.IdentityCandidate(0, "plexon", "id", "one"),
                new ItemService.IdentityCandidate(1, "plexon", "custom_id", "two")));

        assertEquals(ItemService.IdentityStatus.CONFLICT, identity.status());
        assertTrue(identity.id().isEmpty());
        assertEquals(2, identity.sources().size());
    }

    @Test
    void noIdentityIsExplicitlyNone() {
        var identity = ItemService.resolveIdentityCandidates(List.of());
        assertEquals(ItemService.IdentityStatus.NONE, identity.status());
        assertTrue(identity.id().isEmpty());
    }
}
