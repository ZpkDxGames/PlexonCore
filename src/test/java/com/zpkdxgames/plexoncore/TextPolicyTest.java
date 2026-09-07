package com.zpkdxgames.plexoncore;

import com.zpkdxgames.plexoncore.text.TextService;
import org.bukkit.plugin.PluginManager;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;

import static org.junit.jupiter.api.Assertions.*;

class TextPolicyTest {
    private TextService service() {
        PluginManager manager = (PluginManager) Proxy.newProxyInstance(
            PluginManager.class.getClassLoader(), new Class<?>[]{PluginManager.class},
            (proxy, method, args) -> method.getReturnType().isPrimitive() ? primitiveDefault(method.getReturnType()) : null);
        return new TextService(manager);
    }

    @Test void whitelistKeepsFormattingAndRemovesCommands() {
        String sanitized = service().sanitizeMiniMessagePlaceholderOutput("<green>Hello</green><click:run_command:'/op me'>bad</click>");
        assertTrue(sanitized.contains("<green>"));
        assertFalse(sanitized.toLowerCase().contains("click"));
    }

    @Test void whitelistAllowsHexAndGradient() {
        String sanitized = service().sanitizeMiniMessagePlaceholderOutput("<gradient:#88beff:#b9d8ff>Core</gradient> <#ffffff>ok</#ffffff>");
        assertTrue(sanitized.contains("gradient"));
        assertTrue(sanitized.contains("#ffffff"));
    }

    private static Object primitiveDefault(Class<?> type) {
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
