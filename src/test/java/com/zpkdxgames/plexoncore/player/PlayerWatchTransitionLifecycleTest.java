package com.zpkdxgames.plexoncore.player;

import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlayerWatchTransitionLifecycleTest {
    @Test
    void crossWorldMovementPreservesCoherentBeforeAndAfterCoordinates() {
        PlayerWatchService service = new PlayerWatchService();
        UUID playerId = UUID.randomUUID();
        UUID oldWorld = UUID.randomUUID();
        UUID newWorld = UUID.randomUUID();
        var before = new PlayerWatchService.Position(oldWorld, 10.5, 64.0, -3.5);
        var after = new PlayerWatchService.Position(newWorld, 200.0, 80.0, 400.0);

        service.observeMovement(playerId, before, after);
        var activity = service.worldChangeActivity(playerId, oldWorld, after);

        assertEquals(before, activity.from());
        assertEquals(after, activity.to());
        assertTrue(PlayerWatchService.WorldTransition.from(activity).complete());
        service.close();
    }

    @Test
    void missingOldCoordinatesAreExplicitlyUnknownNotDestinationCoordinates() {
        PlayerWatchService service = new PlayerWatchService();
        UUID playerId = UUID.randomUUID();
        UUID oldWorld = UUID.randomUUID();
        var after = new PlayerWatchService.Position(UUID.randomUUID(), 100.0, 70.0, 100.0);

        var activity = service.worldChangeActivity(playerId, oldWorld, after);

        assertEquals(oldWorld, activity.from().worldId());
        assertTrue(Double.isNaN(activity.from().x()));
        assertFalse(PlayerWatchService.WorldTransition.from(activity).complete());
        service.close();
    }

    @Test
    void ownerCleanupIsExactAndRepeatSafe() {
        AtomicBoolean enabledA = new AtomicBoolean(true);
        AtomicBoolean enabledB = new AtomicBoolean(true);
        Plugin ownerA = plugin("A", enabledA);
        Plugin ownerB = plugin("B", enabledB);
        UUID playerId = UUID.randomUUID();
        PlayerWatchService service = new PlayerWatchService();
        service.watch(ownerA, playerId, Set.of(PlayerWatchService.WatchType.DAMAGE), ignored -> {});
        service.watch(ownerB, playerId, Set.of(PlayerWatchService.WatchType.DAMAGE), ignored -> {});

        assertEquals(1, service.purgeOwner(ownerA));
        assertTrue(service.hasWatches(playerId));
        assertEquals(0, service.purgeOwner(ownerA));
        assertEquals(1, service.stats().registrations());
        service.close();
    }

    private static Plugin plugin(String name, AtomicBoolean enabled) {
        return (Plugin) Proxy.newProxyInstance(Plugin.class.getClassLoader(), new Class<?>[]{Plugin.class}, (proxy, method, args) -> switch (method.getName()) {
            case "getName" -> name;
            case "isEnabled" -> enabled.get();
            case "hashCode" -> System.identityHashCode(proxy);
            case "equals" -> proxy == args[0];
            case "toString" -> name;
            default -> defaultValue(method.getReturnType());
        });
    }

    private static Object defaultValue(Class<?> type) {
        if (!type.isPrimitive()) return null;
        if (type == boolean.class) return false;
        if (type == byte.class) return (byte) 0;
        if (type == short.class) return (short) 0;
        if (type == int.class) return 0;
        if (type == long.class) return 0L;
        if (type == float.class) return 0F;
        if (type == double.class) return 0D;
        if (type == char.class) return '\0';
        return null;
    }
}
