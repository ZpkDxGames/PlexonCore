package com.zpkdxgames.plexoncore;

import com.zpkdxgames.plexoncore.api.PlexonCoreAPI.CoreVersion;
import com.zpkdxgames.plexoncore.module.ModuleRegistry;
import com.zpkdxgames.plexoncore.module.ModuleRegistry.ModuleVersionRange;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CoreVersionTest {
    @Test void rangeAcceptsApiOne() {
        ModuleVersionRange range = ModuleVersionRange.parse(">=1.0 <2.0");
        assertTrue(range.contains(CoreVersion.of(1, 0, "1.0.0")));
        assertTrue(range.contains(CoreVersion.of(1, 9, "1.9.0")));
        assertFalse(range.contains(CoreVersion.of(2, 0, "2.0.0")));
    }

    @Test void exactRangeWorks() {
        ModuleVersionRange range = ModuleVersionRange.parse("=1.0");
        assertTrue(range.contains(CoreVersion.of(1, 0, "1.0.0")));
        assertFalse(range.contains(CoreVersion.of(1, 1, "1.1.0")));
    }

    @Test void versionComparisonUsesApiNotPluginVersion() {
        assertTrue(CoreVersion.of(2, 0, "2.0.0").compareTo(CoreVersion.of(1, 9, "99.0.0")) > 0);
    }

    @Test void coreTwoAdvertisesApiOneCompatibilityBridge() {
        ModuleRegistry registry = new ModuleRegistry(CoreVersion.of(2, 0, "2.0.0-SNAPSHOT"), List.of(CoreVersion.of(1, 0, "2.0.0-SNAPSHOT")));
        assertTrue(registry.supportsApi(CoreVersion.of(2, 0, "client")));
        assertTrue(registry.supportsApi(CoreVersion.of(1, 0, "client")));
        assertFalse(registry.supportsApi(CoreVersion.of(1, 1, "client")));
        assertFalse(registry.supportsApi(CoreVersion.of(3, 0, "client")));
    }
}
