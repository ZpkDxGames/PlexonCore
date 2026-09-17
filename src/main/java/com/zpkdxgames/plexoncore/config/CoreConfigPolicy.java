package com.zpkdxgames.plexoncore.config;

import com.zpkdxgames.plexoncore.text.TextService;
import org.bukkit.configuration.file.YamlConfiguration;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Pure policy for Core config validation and restart-required classification. */
final class CoreConfigPolicy {
    private static final List<String> RESTART_REQUIRED_KEYS = List.of(
            "executor.worker-threads",
            "executor.queue-capacity",
            "executor.io-threads",
            "executor.io-queue-capacity",
            "origin-persistence.flush-interval-ms",
            "origin-persistence.batch-size",
            "origin-persistence.pressure-threshold",
            "origin-persistence.max-retries");
    private static final List<String> DEPRECATED_KEYS = List.of("gui.click-sound", "diagnostics.verbose-startup");

    private CoreConfigPolicy() {}

    static ConfigService.ValidationResult validateSchema(YamlConfiguration yaml) {
        int schemaVersion = yaml.getInt("schema-version", -1);
        if (schemaVersion != 1) return ConfigService.ValidationResult.fail("schema-version", "Expected schema-version 1");
        int workers = yaml.getInt("executor.worker-threads", 2);
        int queue = yaml.getInt("executor.queue-capacity", 4096);
        int ioWorkers = yaml.getInt("executor.io-threads", 1);
        int ioQueue = yaml.getInt("executor.io-queue-capacity", 2048);
        if (workers < 1 || workers > 32) return ConfigService.ValidationResult.fail("executor.worker-threads", "Must be between 1 and 32");
        if (queue < 64 || queue > 100_000) return ConfigService.ValidationResult.fail("executor.queue-capacity", "Must be between 64 and 100000");
        if (ioWorkers < 1 || ioWorkers > 8) return ConfigService.ValidationResult.fail("executor.io-threads", "Must be between 1 and 8");
        if (ioQueue < 64 || ioQueue > 100_000) return ConfigService.ValidationResult.fail("executor.io-queue-capacity", "Must be between 64 and 100000");

        int flushMillis = yaml.getInt("origin-persistence.flush-interval-ms", 500);
        int batchSize = yaml.getInt("origin-persistence.batch-size", 512);
        int pressure = yaml.getInt("origin-persistence.pressure-threshold", 2048);
        int retries = yaml.getInt("origin-persistence.max-retries", 6);
        if (flushMillis < 50 || flushMillis > 30_000) return ConfigService.ValidationResult.fail("origin-persistence.flush-interval-ms", "Must be between 50 and 30000");
        if (batchSize < 16 || batchSize > 10_000) return ConfigService.ValidationResult.fail("origin-persistence.batch-size", "Must be between 16 and 10000");
        if (pressure < batchSize || pressure > 100_000) return ConfigService.ValidationResult.fail("origin-persistence.pressure-threshold", "Must be >= batch-size and <= 100000");
        if (retries < 0 || retries > 20) return ConfigService.ValidationResult.fail("origin-persistence.max-retries", "Must be between 0 and 20");

        String mode = yaml.getString("text.placeholder-rendering.default", "SAFE");
        try {
            TextService.TextMode.valueOf(mode.toUpperCase());
        } catch (IllegalArgumentException ex) {
            return ConfigService.ValidationResult.fail("text.placeholder-rendering.default", "Expected SAFE, LEGACY or MINIMESSAGE");
        }
        return ConfigService.ValidationResult.ok();
    }

    static Set<String> restartRequired(ConfigService.ConfigSnapshot candidate, ConfigService.ConfigSnapshot baseline) {
        if (baseline == null) return Set.of();
        Set<String> changed = new LinkedHashSet<>();
        for (String key : RESTART_REQUIRED_KEYS) {
            Object before = baseline.values().get(key);
            Object after = candidate.values().get(key);
            if (!Objects.equals(before, after)) changed.add(key);
        }
        return Collections.unmodifiableSet(changed);
    }

    static List<String> deprecatedPresent(ConfigService.ConfigSnapshot candidate) {
        return DEPRECATED_KEYS.stream().filter(candidate.values()::containsKey).toList();
    }
}
