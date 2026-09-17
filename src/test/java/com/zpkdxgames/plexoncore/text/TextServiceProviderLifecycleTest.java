package com.zpkdxgames.plexoncore.text;

import net.kyori.adventure.text.Component;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TextServiceProviderLifecycleTest {
    @Test
    void providerAbsenceIsSafe() {
        TextService service = new TextService(manager(null));

        assertFalse(service.placeholderProvider().ready());
        assertEquals(Component.text("plain"), service.renderPlaceholderSafe(null, "plain"));
    }

    @Test
    void providerMethodIsCapturedAndRebuiltAcrossLifecycle() {
        AtomicBoolean enabled = new AtomicBoolean(true);
        Plugin provider = plugin("PlaceholderAPI", enabled);
        TextService service = new TextService(manager(provider));
        long initialGeneration = service.placeholderProvider().generation();

        assertTrue(service.placeholderProvider().ready());
        assertEquals(Component.text("resolved:%player_name%"),
                service.renderPlaceholderSafe(null, "%player_name%"));

        enabled.set(false);
        service.invalidateProvider("PlaceholderAPI");
        long disabledGeneration = service.placeholderProvider().generation();
        assertFalse(service.placeholderProvider().ready());
        assertTrue(disabledGeneration > initialGeneration);
        assertEquals(Component.text("raw"), service.renderPlaceholderSafe(null, "raw"));

        enabled.set(true);
        service.refreshProviders();
        assertTrue(service.placeholderProvider().ready());
        assertTrue(service.placeholderProvider().generation() > disabledGeneration);
        assertEquals(Component.text("resolved:again"), service.renderPlaceholderSafe(null, "again"));
    }

    @Test
    void providerInvocationFailureFallsBackAndIsObservable() {
        Plugin provider = plugin("PlaceholderAPI", new AtomicBoolean(true));
        TextService service = new TextService(manager(provider));
        long before = service.placeholderProvider().invocationFailures();

        assertEquals(Component.text("__FAIL__"), service.renderPlaceholderSafe(null, "__FAIL__"));
        assertEquals(before + 1L, service.placeholderProvider().invocationFailures());
        assertTrue(service.placeholderProvider().ready());
    }

    private static PluginManager manager(Plugin provider) {
        return (PluginManager) Proxy.newProxyInstance(
                PluginManager.class.getClassLoader(), new Class<?>[]{PluginManager.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("getPlugin") && args != null && args.length == 1
                            && "PlaceholderAPI".equalsIgnoreCase(String.valueOf(args[0]))) return provider;
                    return defaultValue(method.getReturnType());
                });
    }

    private static Plugin plugin(String name, AtomicBoolean enabled) {
        return (Plugin) Proxy.newProxyInstance(
                Plugin.class.getClassLoader(), new Class<?>[]{Plugin.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getName" -> name;
                    case "isEnabled" -> enabled.get();
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
