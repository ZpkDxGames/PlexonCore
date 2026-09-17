package com.zpkdxgames.plexoncore.config;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CoreConfigPolicyTest {
    @Test
    void validCandidateAcceptsFreshDefaultsAndDeprecatedKeysRemainTolerated() {
        YamlConfiguration yaml = base();
        yaml.set("gui.click-sound", "UI_BUTTON_CLICK");
        yaml.set("diagnostics.verbose-startup", true);

        assertTrue(CoreConfigPolicy.validateSchema(yaml).valid());
        var snapshot = ConfigService.ConfigSnapshot.from(yaml);
        assertEquals(2, CoreConfigPolicy.deprecatedPresent(snapshot).size());
    }

    @Test
    void futureOrInvalidSchemaCandidateIsRejectedBeforePublication() {
        YamlConfiguration future = base();
        future.set("schema-version", 2);
        assertFalse(CoreConfigPolicy.validateSchema(future).valid());

        YamlConfiguration invalid = base();
        invalid.set("origin-persistence.batch-size", 2048);
        invalid.set("origin-persistence.pressure-threshold", 1024);
        var result = CoreConfigPolicy.validateSchema(invalid);
        assertFalse(result.valid());
        assertEquals("origin-persistence.pressure-threshold", result.path());
    }

    @Test
    void runtimeSizingChangesAreExplicitlyRestartRequired() {
        YamlConfiguration beforeYaml = base();
        YamlConfiguration afterYaml = base();
        afterYaml.set("executor.worker-threads", 4);
        afterYaml.set("origin-persistence.flush-interval-ms", 750);

        var changed = CoreConfigPolicy.restartRequired(
                ConfigService.ConfigSnapshot.from(afterYaml),
                ConfigService.ConfigSnapshot.from(beforeYaml));

        assertTrue(changed.contains("executor.worker-threads"));
        assertTrue(changed.contains("origin-persistence.flush-interval-ms"));
        assertEquals(2, changed.size());
    }

    @Test
    void hotSafeTextSettingDoesNotClaimRestartIsRequired() {
        YamlConfiguration beforeYaml = base();
        YamlConfiguration afterYaml = base();
        afterYaml.set("text.placeholder-rendering.default", "LEGACY");

        assertTrue(CoreConfigPolicy.restartRequired(
                ConfigService.ConfigSnapshot.from(afterYaml),
                ConfigService.ConfigSnapshot.from(beforeYaml)).isEmpty());
    }

    @Test
    void publishedSnapshotIsImmutable() {
        var snapshot = ConfigService.ConfigSnapshot.from(base());
        assertThrows(UnsupportedOperationException.class,
                () -> snapshot.values().put("executor.worker-threads", 99));
        assertTrue(snapshot.values().containsKey("schema-version"));
    }

    private static YamlConfiguration base() {
        YamlConfiguration yaml = new YamlConfiguration();
        Map.<String, Object>ofEntries(
                Map.entry("schema-version", 1),
                Map.entry("executor.worker-threads", 2),
                Map.entry("executor.queue-capacity", 4096),
                Map.entry("executor.io-threads", 1),
                Map.entry("executor.io-queue-capacity", 2048),
                Map.entry("origin-persistence.flush-interval-ms", 500),
                Map.entry("origin-persistence.batch-size", 512),
                Map.entry("origin-persistence.pressure-threshold", 2048),
                Map.entry("origin-persistence.max-retries", 6),
                Map.entry("text.placeholder-rendering.default", "SAFE")
        ).forEach(yaml::set);
        return yaml;
    }
}
