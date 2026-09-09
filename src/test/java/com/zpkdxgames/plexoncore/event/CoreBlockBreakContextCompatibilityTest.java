package com.zpkdxgames.plexoncore.event;

import com.zpkdxgames.plexoncore.context.BlockOrigin;
import com.zpkdxgames.plexoncore.context.CoreBlockBreakContext;
import org.bukkit.Material;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class CoreBlockBreakContextCompatibilityTest {
    @Test void legacyConstructorRetainsHistoricalDropAssumption() {
        UUID player = UUID.randomUUID();
        UUID world = UUID.randomUUID();
        CoreBlockBreakContext context = new CoreBlockBreakContext(
                1L, player, "Player", world, "world", 1, 64, 2,
                Material.STONE, null, BlockOrigin.NATURAL, 20L, 30L);
        assertTrue(context.dropItems());
        assertEquals(BlockOrigin.NATURAL, context.origin());
    }

    @Test void finalDropOutcomeCanBeReboundWithoutChangingOrigin() {
        CoreBlockBreakContext captured = new CoreBlockBreakContext(
                2L, UUID.randomUUID(), "Player", UUID.randomUUID(), "world", 3, 70, 4,
                Material.DIAMOND_ORE, null, BlockOrigin.PLAYER_PLACED, 40L, 50L, true);
        CoreBlockBreakContext finalContext = captured.withDropItems(false);
        assertFalse(finalContext.dropItems());
        assertEquals(captured.origin(), finalContext.origin());
        assertEquals(captured.eventId(), finalContext.eventId());
    }
}
