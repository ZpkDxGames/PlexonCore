package com.zpkdxgames.plexoncore.scheduler;

import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CoreSchedulerObservabilityTest {
    @Test
    void asyncFailureIsVisibleAndLaterSuccessRecoversContributorState() {
        CoreScheduler scheduler = new CoreScheduler(plugin("Core", new AtomicBoolean(true)), 1, 64, 1, 64);
        try {
            assertThrows(CompletionException.class, () -> scheduler.runAsync(() -> {
                throw new IllegalStateException("synthetic");
            }).join());
            assertEquals(CoreScheduler.HealthState.DEGRADED, scheduler.health().state());
            assertEquals(1, scheduler.failedTasks());

            scheduler.runAsync(() -> {}).join();
            assertEquals(CoreScheduler.HealthState.READY, scheduler.health().state());
            assertEquals(1, scheduler.failedTasks());
        } finally {
            scheduler.close();
        }
    }

    @Test
    void disabledOwnerCannotStartNewAsyncCallback() {
        AtomicBoolean enabled = new AtomicBoolean(false);
        Plugin owner = plugin("Disabled", enabled);
        CoreScheduler scheduler = new CoreScheduler(plugin("Core", new AtomicBoolean(true)), 1, 64, 1, 64);
        try {
            assertThrows(CompletionException.class, () -> scheduler.runAsync(owner, () -> {
                throw new AssertionError("disabled callback ran");
            }).join());
        } finally {
            scheduler.close();
        }
    }

    @Test
    void ownerCleanupCancelsTrackedAsyncWorkWithoutTouchingOtherOwner() throws Exception {
        Plugin core = plugin("Core", new AtomicBoolean(true));
        Plugin ownerA = plugin("OwnerA", new AtomicBoolean(true));
        Plugin ownerB = plugin("OwnerB", new AtomicBoolean(true));
        CoreScheduler scheduler = new CoreScheduler(core, 1, 64, 1, 64);
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        try {
            var a = scheduler.runAsync(ownerA, () -> {
                started.countDown();
                try { release.await(2, TimeUnit.SECONDS); }
                catch (InterruptedException ex) { Thread.currentThread().interrupt(); }
            });
            assertTrue(started.await(1, TimeUnit.SECONDS));
            var queuedA = scheduler.runAsync(ownerA, () -> {});
            var queuedB = scheduler.runAsync(ownerB, () -> {});

            int cancelled = scheduler.purgeOwner(ownerA);
            release.countDown();
            queuedB.join();

            assertTrue(cancelled >= 1);
            assertTrue(a.isCancelled() || a.isCompletedExceptionally());
            assertTrue(queuedA.isCancelled() || queuedA.isCompletedExceptionally());
            assertTrue(queuedB.isDone());
        } finally {
            release.countDown();
            scheduler.close();
        }
    }

    @Test
    void shutdownRejectsNewAsyncWorkAndIsIdempotent() {
        CoreScheduler scheduler = new CoreScheduler(plugin("Core", new AtomicBoolean(true)), 1, 64, 1, 64);
        scheduler.close();
        scheduler.close();

        assertThrows(CompletionException.class, () -> scheduler.runAsync(() -> {}).join());
    }

    private static Plugin plugin(String name, AtomicBoolean enabled) {
        Logger logger = Logger.getLogger("CoreSchedulerObservabilityTest-" + name);
        return (Plugin) Proxy.newProxyInstance(Plugin.class.getClassLoader(), new Class<?>[]{Plugin.class}, (proxy, method, args) -> switch (method.getName()) {
            case "getName" -> name;
            case "isEnabled" -> enabled.get();
            case "getLogger" -> logger;
            case "hashCode" -> System.identityHashCode(proxy);
            case "equals" -> proxy == args[0];
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
