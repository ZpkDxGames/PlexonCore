package com.zpkdxgames.plexoncore.integration;

import org.bukkit.plugin.PluginManager;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IntegrationRegistryTest {
    @Test void activeClaimIntegrationUsesGpFlagsIdentity() {
        PluginManager pluginManager = (PluginManager) Proxy.newProxyInstance(
                PluginManager.class.getClassLoader(), new Class<?>[]{PluginManager.class},
                (proxy, method, args) -> defaultValue(method.getReturnType()));
        IntegrationRegistry registry = new IntegrationRegistry(pluginManager);

        registry.refresh();

        assertTrue(registry.get("PLEXON_GP_FLAGS").isPresent());
        assertEquals("PlexonGPFlags", registry.get("PLEXON_GP_FLAGS").orElseThrow().provider());
        assertFalse(registry.get("PLEXON_CLAIM_FLAGS").isPresent());
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
