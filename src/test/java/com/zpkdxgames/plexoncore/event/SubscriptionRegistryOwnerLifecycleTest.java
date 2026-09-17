package com.zpkdxgames.plexoncore.event;

import org.bukkit.Material;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SubscriptionRegistryOwnerLifecycleTest {
    @Test
    void purgingOneOwnerLeavesOtherOwnerOperationalAndIsIdempotent() {
        AtomicBoolean enabledA = new AtomicBoolean(true);
        AtomicBoolean enabledB = new AtomicBoolean(true);
        Plugin ownerA = plugin("OwnerA", enabledA);
        Plugin ownerB = plugin("OwnerB", enabledB);
        SubscriptionRegistry registry = new SubscriptionRegistry();
        CoreBlockSubscription subscription = CoreBlockSubscription.builder().material(Material.STONE).build();

        registry.subscribe(ownerA, "a", subscription, ignored -> {});
        registry.subscribe(ownerB, "b", subscription, ignored -> {});
        assertEquals(2, registry.plan(Material.STONE).subscribers().size());

        assertEquals(1, registry.purgeOwner(ownerA));
        assertEquals(1, registry.plan(Material.STONE).subscribers().size());
        assertEquals("b", registry.plan(Material.STONE).subscribers().getFirst().moduleId());
        assertEquals(0, registry.purgeOwner(ownerA));
    }

    @Test
    void disabledOwnerFailsClosedEvenBeforeCleanupRuns() {
        AtomicBoolean enabled = new AtomicBoolean(true);
        Plugin owner = plugin("Owner", enabled);
        SubscriptionRegistry registry = new SubscriptionRegistry();
        CoreBlockSubscription subscription = CoreBlockSubscription.builder().material(Material.STONE).build();
        registry.subscribe(owner, "owner", subscription, ignored -> { throw new AssertionError("disabled owner callback ran"); });
        enabled.set(false);

        assertEquals(0, registry.plan(Material.STONE).subscribers().stream().filter(SubscriptionRegistry.Subscriber::ownerEnabled).count());
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
