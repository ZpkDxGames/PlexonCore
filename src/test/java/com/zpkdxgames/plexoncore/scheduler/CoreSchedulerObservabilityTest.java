package com.zpkdxgames.plexoncore.scheduler;

import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

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
