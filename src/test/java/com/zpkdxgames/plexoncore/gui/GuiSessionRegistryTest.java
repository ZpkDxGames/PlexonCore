package com.zpkdxgames.plexoncore.gui;

import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GuiSessionRegistryTest {
    @Test
    void newerGenerationMakesOldHolderStaleAndCloseCannotRemoveReplacement() {
        GuiSessionRegistry registry = new GuiSessionRegistry();
        UUID player = UUID.randomUUID();
        Plugin owner = plugin("Owner");
        GuiService.GuiSession first = session(player, "first");
        GuiService.GuiSession second = session(player, "second");

        registry.put(player, first, owner, 1L);
        registry.put(player, second, owner, 2L);

        assertFalse(registry.current(player, first, owner, 1L));
        assertTrue(registry.current(player, second, owner, 2L));
        assertFalse(registry.removeIfCurrent(player, first, owner, 1L));
        assertEquals(second, registry.session(player).orElseThrow());
        assertTrue(registry.removeIfCurrent(player, second, owner, 2L));
        assertTrue(registry.session(player).isEmpty());
    }

    @Test
    void purgeOwnerIsExactInstanceScopedAndIdempotent() {
        GuiSessionRegistry registry = new GuiSessionRegistry();
        Plugin ownerA = plugin("SameName");
        Plugin ownerB = plugin("SameName");
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();

        registry.put(a, session(a, "a"), ownerA, 1L);
        registry.put(b, session(b, "b"), ownerB, 2L);

        assertEquals(1, registry.purgeOwner(ownerA).size());
        assertTrue(registry.session(a).isEmpty());
        assertTrue(registry.session(b).isPresent());
        assertEquals(0, registry.purgeOwner(ownerA).size());
        assertEquals(1, registry.size());
    }

    @Test
    void ownerlessCompatibilitySessionStillUsesGenerationAuthority() {
        GuiSessionRegistry registry = new GuiSessionRegistry();
        UUID player = UUID.randomUUID();
        GuiService.GuiSession session = session(player, "legacy");

        registry.put(player, session, null, 7L);

        assertTrue(registry.current(player, session, null, 7L));
        assertFalse(registry.current(player, session, null, 6L));
    }

    private static GuiService.GuiSession session(UUID playerId, String guiId) {
        return new GuiService.GuiSession(playerId, "test", guiId, 0, Instant.EPOCH);
    }

    private static Plugin plugin(String name) {
        return (Plugin) Proxy.newProxyInstance(Plugin.class.getClassLoader(), new Class<?>[]{Plugin.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getName" -> name;
                    case "isEnabled" -> true;
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
