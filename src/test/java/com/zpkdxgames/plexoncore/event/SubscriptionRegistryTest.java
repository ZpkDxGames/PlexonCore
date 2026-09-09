package com.zpkdxgames.plexoncore.event;

import org.bukkit.Material;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SubscriptionRegistryTest {
    @Test void compilesOnlyInterestedMaterialRoutesAndUnionsRequirements() throws Exception {
        SubscriptionRegistry registry = new SubscriptionRegistry();
        AutoCloseable tools = registry.subscribe("plexontools",
            CoreBlockSubscription.builder().material(Material.STONE).requiresNaturalOrigin(true).requiresMainHandIdentity("plexontools").build(),
            context -> {});
        AutoCloseable quests = registry.subscribe("plexonquests",
            CoreBlockSubscription.builder().material(Material.STONE).build(), context -> {});

        var stone = registry.plan(Material.STONE);
        assertFalse(stone.empty());
        assertEquals(2, stone.subscribers().size());
        assertTrue(stone.requiresNaturalOrigin());
        assertEquals(java.util.Set.of("plexontools"), stone.itemIdentityNamespaces());
        assertTrue(registry.plan(Material.DIRT).empty());
        assertEquals(1, registry.routeCount());

        tools.close();
        var afterTools = registry.plan(Material.STONE);
        assertEquals(1, afterTools.subscribers().size());
        assertFalse(afterTools.requiresNaturalOrigin());
        assertTrue(afterTools.itemIdentityNamespaces().isEmpty());
        quests.close();
        assertTrue(registry.plan(Material.STONE).empty());
        assertEquals(0, registry.routeCount());
    }
}
