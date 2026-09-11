package com.zpkdxgames.plexoncore.player;

import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlayerWatchServiceDispatchTest {
    @Test void failingWatchCannotBlockOtherSubscribers() {
        UUID playerId = UUID.randomUUID();
        AtomicInteger failures = new AtomicInteger();
        AtomicInteger delivered = new AtomicInteger();
        PlayerWatchService service = new PlayerWatchService((watchId, failure) -> failures.incrementAndGet());
        service.watch(playerId, Set.of(PlayerWatchService.WatchType.DAMAGE), activity -> {
            throw new IllegalStateException("synthetic failure");
        });
        service.watch(playerId, Set.of(PlayerWatchService.WatchType.DAMAGE), activity -> delivered.incrementAndGet());

        var activity = new PlayerWatchService.PlayerActivity(
                playerId, PlayerWatchService.Signal.DAMAGED, null, null, 2.0D, System.nanoTime());
        int successful = service.dispatchWatchedActivity(playerId, PlayerWatchService.WatchType.DAMAGE, activity);

        assertEquals(1, failures.get());
        assertEquals(1, delivered.get());
        assertEquals(1, successful);
        assertTrue(service.hasWatches(playerId));
        service.close();
    }

    @Test void failureReporterCannotBreakDispatchIsolation() {
        UUID playerId = UUID.randomUUID();
        AtomicInteger delivered = new AtomicInteger();
        PlayerWatchService service = new PlayerWatchService((watchId, failure) -> {
            throw new IllegalStateException("reporter failure");
        });
        service.watch(playerId, Set.of(PlayerWatchService.WatchType.MOVEMENT), activity -> {
            throw new IllegalArgumentException("subscriber failure");
        });
        service.watch(playerId, Set.of(PlayerWatchService.WatchType.MOVEMENT), activity -> delivered.incrementAndGet());

        var activity = new PlayerWatchService.PlayerActivity(
                playerId, PlayerWatchService.Signal.MOVED, null, null, 0.0D, System.nanoTime());
        assertEquals(1, service.dispatchWatchedActivity(playerId, PlayerWatchService.WatchType.MOVEMENT, activity));
        assertEquals(1, delivered.get());
        service.close();
    }
}
