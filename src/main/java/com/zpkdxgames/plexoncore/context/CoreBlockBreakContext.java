package com.zpkdxgames.plexoncore.context;

import org.bukkit.Material;

import java.util.Objects;
import java.util.UUID;

public record CoreBlockBreakContext(
    long eventId,
    UUID playerId,
    String playerName,
    UUID worldId,
    String worldName,
    int x,
    int y,
    int z,
    Material material,
    CoreItemIdentity mainHand,
    BlockOrigin origin,
    long gameTick,
    long createdNanos,
    boolean dropItems
) {
    public CoreBlockBreakContext {
        if (eventId <= 0) throw new IllegalArgumentException("eventId must be positive");
        playerId = Objects.requireNonNull(playerId, "playerId");
        playerName = Objects.requireNonNullElse(playerName, "unknown");
        worldId = Objects.requireNonNull(worldId, "worldId");
        worldName = Objects.requireNonNullElse(worldName, "unknown");
        material = Objects.requireNonNull(material, "material");
        origin = origin == null ? BlockOrigin.UNKNOWN : origin;
    }

    /**
     * Compatibility constructor matching the Core 2.0.0 context signature. Callers compiled against the
     * original constructor continue to link; contexts created through it retain the historical assumption
     * that drops are enabled.
     */
    public CoreBlockBreakContext(long eventId, UUID playerId, String playerName, UUID worldId, String worldName,
                                 int x, int y, int z, Material material, CoreItemIdentity mainHand,
                                 BlockOrigin origin, long gameTick, long createdNanos) {
        this(eventId, playerId, playerName, worldId, worldName, x, y, z, material, mainHand, origin,
                gameTick, createdNanos, true);
    }

    public boolean hasMainHandIdentity() { return mainHand != null; }

    public CoreBlockBreakContext withDropItems(boolean value) {
        return new CoreBlockBreakContext(eventId, playerId, playerName, worldId, worldName, x, y, z,
                material, mainHand, origin, gameTick, createdNanos, value);
    }
}
