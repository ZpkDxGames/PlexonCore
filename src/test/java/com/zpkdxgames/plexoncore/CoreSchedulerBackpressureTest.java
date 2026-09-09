package com.zpkdxgames.plexoncore;

import com.zpkdxgames.plexoncore.scheduler.CoreScheduler;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CoreSchedulerBackpressureTest {
    @Test void computeLaneRejectsBeyondItsBoundedQueue() throws Exception {
        Plugin plugin = (Plugin) Proxy.newProxyInstance(
            Plugin.class.getClassLoader(), new Class<?>[]{Plugin.class},
            (proxy, method, args) -> switch (method.getName()) {
                case "getName" -> "CoreSchedulerTest";
                case "isEnabled" -> true;
                default -> defaultValue(method.getReturnType());
            });
        CoreScheduler scheduler = new CoreScheduler(plugin, 1, 64, 1, 64);
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        try {
            scheduler.runAsync(() -> {
                started.countDown();
                try { release.await(5, TimeUnit.SECONDS); }
                catch (InterruptedException ex) { Thread.currentThread().interrupt(); }
            });
            assertTrue(started.await(2, TimeUnit.SECONDS));
            for (int i = 0; i < 64; i++) scheduler.runAsync(() -> {});
            var rejected = scheduler.runAsync(() -> {});
            assertTrue(rejected.isCompletedExceptionally());
            assertEquals(1L, scheduler.rejectedComputeTasks());
            assertEquals(64, scheduler.computeQueueSize());
        } finally {
            release.countDown();
            scheduler.close();
        }
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
