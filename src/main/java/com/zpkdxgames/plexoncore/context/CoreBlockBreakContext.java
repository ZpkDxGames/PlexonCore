package com.zpkdxgames.plexoncore.context;

import org.bukkit.Material;

import java.util.Objects;
import java.util.UUID;

public record CoreBlockBreakContext(
    UUID eventId,
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
    long createdNanos
) {
    public CoreBlockBreakContext {
        eventId = Objects.requireNonNull(eventId, "eventId");
        playerId = Objects.requireNonNull(playerId, "playerId");
        playerName = Objects.requireNonNullElse(playerName, "unknown");
        worldId = Objects.requireNonNull(worldId, "worldId");
        worldName = Objects.requireNonNullElse(worldName, "unknown");
        material = Objects.requireNonNull(material, "material");
        origin = origin == null ? BlockOrigin.UNKNOWN : origin;
    }

    public boolean hasMainHandIdentity() {
        return mainHand != null;
    }
}
