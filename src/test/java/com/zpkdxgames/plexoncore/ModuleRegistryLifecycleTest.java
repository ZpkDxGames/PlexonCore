package com.zpkdxgames.plexoncore;

import com.zpkdxgames.plexoncore.api.PlexonCoreAPI.CoreVersion;
import com.zpkdxgames.plexoncore.module.ModuleRegistry;
import com.zpkdxgames.plexoncore.module.ModuleRegistry.ModuleDescriptor;
import com.zpkdxgames.plexoncore.module.ModuleRegistry.ModuleState;
import com.zpkdxgames.plexoncore.module.ModuleRegistry.ModuleVersionRange;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.time.Instant;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class ModuleRegistryLifecycleTest {
    @Test
    void disabledOwnerCanBeReplacedByFreshPluginInstance() {
        ModuleRegistry registry = registry();
        Plugin oldOwner = plugin("PlexonExample", false);
        Plugin newOwner = plugin("PlexonExample", true);

        assertTrue(registry.register(descriptor("example", oldOwner, ModuleState.READY, api2())).success());
        var replacement = registry.register(descriptor("example", newOwner, ModuleState.STARTING, api2()));

        assertTrue(replacement.success());
        assertSame(newOwner, registry.find("example").orElseThrow().plugin());
        assertEquals(ModuleState.STARTING, registry.find("example").orElseThrow().state());
    }

    @Test
    void unregisterOwnedByUsesExactPluginIdentity() {
        ModuleRegistry registry = registry();
        Plugin oldOwner = plugin("PlexonExample", true);
        Plugin otherOwner = plugin("PlexonOther", true);

        assertTrue(registry.register(descriptor("example", oldOwner, ModuleState.READY, api2())).success());
        assertTrue(registry.register(descriptor("other", otherOwner, ModuleState.READY, api2())).success());

        assertEquals(1, registry.unregisterOwnedBy(oldOwner));
        assertTrue(registry.find("example").isEmpty());
        assertSame(otherOwner, registry.find("other").orElseThrow().plugin());
    }

    @Test
    void lateStateUpdateFromOldOwnerCannotMutateReplacement() {
        ModuleRegistry registry = registry();
        Plugin oldOwner = plugin("PlexonExample", false);
        Plugin newOwner = plugin("PlexonExample", true);

        assertTrue(registry.register(descriptor("example", oldOwner, ModuleState.STARTING, api2())).success());
        assertTrue(registry.register(descriptor("example", newOwner, ModuleState.STARTING, api2())).success());

        assertFalse(registry.updateState("example", oldOwner, ModuleState.READY, "late old callback"));
        assertEquals(ModuleState.STARTING, registry.find("example").orElseThrow().state());
        assertTrue(registry.updateState("example", newOwner, ModuleState.READY, "ready"));
        assertEquals(ModuleState.READY, registry.find("example").orElseThrow().state());
    }

    @Test
    void incompatibleDuplicateCannotOverwriteActiveOwner() {
        ModuleRegistry registry = registry();
        Plugin active = plugin("PlexonActive", true);
        Plugin conflicting = plugin("PlexonConflict", true);

        assertTrue(registry.register(descriptor("shared", active, ModuleState.READY, api2())).success());
        var result = registry.register(descriptor("shared", conflicting, ModuleState.STARTING,
            ModuleVersionRange.parse(">=3.0 <4.0")));

        assertFalse(result.success());
        assertSame(active, registry.find("shared").orElseThrow().plugin());
        assertEquals(ModuleState.READY, registry.find("shared").orElseThrow().state());
    }

    private static ModuleRegistry registry() {
        return new ModuleRegistry(CoreVersion.of(2, 0, "2.0.4"));
    }

    private static ModuleVersionRange api2() {
        return ModuleVersionRange.parse(">=2.0 <3.0");
    }

    private static ModuleDescriptor descriptor(String id, Plugin plugin, ModuleState state, ModuleVersionRange range) {
        return new ModuleDescriptor(id, plugin.getName(), plugin.getName(), "test", plugin, range,
            Set.of("test"), state, state.name(), Instant.now());
    }

    private static Plugin plugin(String name, boolean enabled) {
        return (Plugin) Proxy.newProxyInstance(
            Plugin.class.getClassLoader(),
            new Class<?>[]{Plugin.class},
            (proxy, method, args) -> switch (method.getName()) {
                case "getName" -> name;
                case "isEnabled" -> enabled;
                case "toString" -> name + "[enabled=" + enabled + "]";
                case "hashCode" -> System.identityHashCode(proxy);
                case "equals" -> proxy == args[0];
                default -> defaultValue(method.getReturnType());
            }
        );
    }

    private static Object defaultValue(Class<?> type) {
        if (!type.isPrimitive()) return null;
        if (type == boolean.class) return false;
        if (type == char.class) return '\0';
        if (type == byte.class) return (byte) 0;
        if (type == short.class) return (short) 0;
        if (type == int.class) return 0;
        if (type == long.class) return 0L;
        if (type == float.class) return 0F;
        if (type == double.class) return 0D;
        throw new IllegalStateException("Unsupported primitive: " + type);
    }
}
