package com.zpkdxgames.plexoncore.config;

import com.zpkdxgames.plexoncore.text.TextService;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

public final class ConfigService {
    private static final DateTimeFormatter BACKUP_STAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").withZone(ZoneOffset.UTC);
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

    private final JavaPlugin plugin;
    private final TextService textService;
    private final Path root;
    private final AtomicReference<ConfigSnapshot> coreSnapshot = new AtomicReference<>(ConfigSnapshot.empty());
    private final AtomicReference<Set<String>> restartRequiredSettings = new AtomicReference<>(Set.of());
    private final Set<String> loggedDeprecations = ConcurrentHashMap.newKeySet();
    private volatile ConfigSnapshot startupSnapshot;
    private volatile ValidationResult lastValidation = ValidationResult.ok();
    private volatile long successfulReloads;
    private volatile long failedReloads;

    public ConfigService(JavaPlugin plugin, TextService textService) {
        this.plugin = Objects.requireNonNull(plugin);
        this.textService = Objects.requireNonNull(textService);
        this.root = plugin.getDataFolder().toPath().toAbsolutePath().normalize();
    }

    public synchronized ValidationResult initializeCore() {
        try {
            Files.createDirectories(root);
            Path config = resolveSafe("config.yml");
            if (Files.notExists(config)) copyBundled("config.yml", config);
            ValidationResult result = reloadCore();
            if (result.valid()) {
                startupSnapshot = coreSnapshot.get();
                restartRequiredSettings.set(Set.of());
            }
            return result;
        } catch (Exception ex) {
            failedReloads++;
            lastValidation = ValidationResult.fail("config.yml", ex.getMessage());
            return lastValidation;
        }
    }

    /** Prepare -> validate -> commit one immutable Core config generation. */
    public synchronized ValidationResult reloadCore() {
        try {
            Path config = resolveSafe("config.yml");
            LoadResult result = loadCandidate(config, List.of(this::validateCoreSchema, this::validateConfiguredMiniMessage));
            if (!result.validation().valid()) {
                failedReloads++;
                lastValidation = result.validation();
                return result.validation();
            }

            ConfigSnapshot candidate = result.snapshot();
            logDeprecatedKeys(candidate);
            Set<String> restartRequired = compareRestartRequired(candidate, startupSnapshot);
            coreSnapshot.set(candidate);
            restartRequiredSettings.set(restartRequired);
            successfulReloads++;
            lastValidation = ValidationResult.ok();
            return lastValidation;
        } catch (Exception ex) {
            failedReloads++;
            lastValidation = ValidationResult.fail("config.yml", ex.getMessage());
            return lastValidation;
        }
    }

    public LoadResult load(String relativePath, List<ConfigValidator> validators) {
        try {
            return loadCandidate(resolveSafe(relativePath), validators == null ? List.of() : validators);
        } catch (Exception ex) {
            return new LoadResult(ConfigSnapshot.empty(), ValidationResult.fail(relativePath, ex.getMessage()));
        }
    }

    public Path resolveSafe(String relativePath) {
        Objects.requireNonNull(relativePath, "relativePath");
        Path candidate = root.resolve(relativePath).normalize();
        if (!candidate.startsWith(root)) throw new IllegalArgumentException("Path traversal rejected: " + relativePath);
        return candidate;
    }

    public Path backup(String relativePath) throws IOException {
        Path source = resolveSafe(relativePath);
        if (Files.notExists(source)) throw new IOException("Cannot back up missing file: " + relativePath);
        Path backups = root.resolve("backups").normalize();
        Files.createDirectories(backups);
        String safeName = source.getFileName().toString().replaceAll("[^A-Za-z0-9._-]", "_");
        Path destination = backups.resolve(safeName + "." + BACKUP_STAMP.format(Instant.now()) + ".bak");
        Files.copy(source, destination, StandardCopyOption.COPY_ATTRIBUTES);
        return destination;
    }

    public void atomicWrite(String relativePath, String content) throws IOException {
        Path target = resolveSafe(relativePath);
        Files.createDirectories(target.getParent());
        Path temp = Files.createTempFile(target.getParent(), target.getFileName().toString(), ".tmp");
        try {
            Files.writeString(temp, content, StandardCharsets.UTF_8, StandardOpenOption.TRUNCATE_EXISTING);
            try {
                Files.move(temp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException unsupportedAtomicMove) {
                Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temp);
        }
    }

    public MigrationResult migrate(String relativePath, int targetVersion, List<ConfigMigration> migrations) {
        try {
            Path path = resolveSafe(relativePath);
            YamlConfiguration yaml = loadYaml(path);
            int current = yaml.getInt("schema-version", 0);
            int originalVersion = current;
            if (current > targetVersion) {
                return new MigrationResult(false, current, current, null,
                        "Future schema " + current + " is newer than supported schema " + targetVersion);
            }
            if (current == targetVersion) return new MigrationResult(true, current, current, null, "Already current");
            Path backup = backup(relativePath);
            Map<Integer, ConfigMigration> byFrom = new LinkedHashMap<>();
            for (ConfigMigration migration : migrations) byFrom.put(migration.fromVersion(), migration);
            while (current < targetVersion) {
                ConfigMigration migration = byFrom.get(current);
                if (migration == null || migration.toVersion() <= current) {
                    return new MigrationResult(false, originalVersion, current, backup, "Missing migration from schema " + current);
                }
                migration.apply(yaml);
                current = migration.toVersion();
                yaml.set("schema-version", current);
            }
            atomicWrite(relativePath, yaml.saveToString());
            return new MigrationResult(true, originalVersion, current, backup, "Migrated");
        } catch (Exception ex) {
            return new MigrationResult(false, -1, -1, null, ex.getMessage());
        }
    }

    public ConfigSnapshot core() { return coreSnapshot.get(); }
    public ValidationResult lastValidation() { return lastValidation; }
    public long successfulReloads() { return successfulReloads; }
    public long failedReloads() { return failedReloads; }
    public Set<String> restartRequiredSettings() { return restartRequiredSettings.get(); }
    public boolean restartRequired() { return !restartRequiredSettings.get().isEmpty(); }

    private LoadResult loadCandidate(Path path, List<ConfigValidator> validators) throws IOException, InvalidConfigurationException {
        YamlConfiguration yaml = loadYaml(path);
        for (ConfigValidator validator : validators) {
            ValidationResult validation = validator.validate(yaml);
            if (!validation.valid()) return new LoadResult(ConfigSnapshot.empty(), validation);
        }
        return new LoadResult(ConfigSnapshot.from(yaml), ValidationResult.ok());
    }

    private YamlConfiguration loadYaml(Path path) throws IOException, InvalidConfigurationException {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.load(path.toFile());
        return yaml;
    }

    private ValidationResult validateCoreSchema(YamlConfiguration yaml) {
        int schemaVersion = yaml.getInt("schema-version", -1);
        if (schemaVersion != 1) return ValidationResult.fail("schema-version", "Expected schema-version 1");
        int workers = yaml.getInt("executor.worker-threads", 2);
        int queue = yaml.getInt("executor.queue-capacity", 4096);
        int ioWorkers = yaml.getInt("executor.io-threads", 1);
        int ioQueue = yaml.getInt("executor.io-queue-capacity", 2048);
        if (workers < 1 || workers > 32) return ValidationResult.fail("executor.worker-threads", "Must be between 1 and 32");
        if (queue < 64 || queue > 100_000) return ValidationResult.fail("executor.queue-capacity", "Must be between 64 and 100000");
        if (ioWorkers < 1 || ioWorkers > 8) return ValidationResult.fail("executor.io-threads", "Must be between 1 and 8");
        if (ioQueue < 64 || ioQueue > 100_000) return ValidationResult.fail("executor.io-queue-capacity", "Must be between 64 and 100000");

        int flushMillis = yaml.getInt("origin-persistence.flush-interval-ms", 500);
        int batchSize = yaml.getInt("origin-persistence.batch-size", 512);
        int pressure = yaml.getInt("origin-persistence.pressure-threshold", 2048);
        int retries = yaml.getInt("origin-persistence.max-retries", 6);
        if (flushMillis < 50 || flushMillis > 30_000) return ValidationResult.fail("origin-persistence.flush-interval-ms", "Must be between 50 and 30000");
        if (batchSize < 16 || batchSize > 10_000) return ValidationResult.fail("origin-persistence.batch-size", "Must be between 16 and 10000");
        if (pressure < batchSize || pressure > 100_000) return ValidationResult.fail("origin-persistence.pressure-threshold", "Must be >= batch-size and <= 100000");
        if (retries < 0 || retries > 20) return ValidationResult.fail("origin-persistence.max-retries", "Must be between 0 and 20");

        String mode = yaml.getString("text.placeholder-rendering.default", "SAFE");
        try {
            TextService.TextMode.valueOf(mode.toUpperCase());
        } catch (IllegalArgumentException ex) {
            return ValidationResult.fail("text.placeholder-rendering.default", "Expected SAFE, LEGACY or MINIMESSAGE");
        }
        return ValidationResult.ok();
    }

    private ValidationResult validateConfiguredMiniMessage(YamlConfiguration yaml) {
        for (String key : yaml.getKeys(true)) {
            if (!key.toLowerCase().contains("message") && !key.toLowerCase().contains("title") && !key.toLowerCase().contains("prefix")) continue;
            Object value = yaml.get(key);
            if (!(value instanceof String string) || !string.contains("<")) continue;
            TextService.ValidationResult result = textService.validateMiniMessage(string);
            if (!result.valid()) return ValidationResult.fail(key, result.reason());
        }
        return ValidationResult.ok();
    }

    private Set<String> compareRestartRequired(ConfigSnapshot candidate, ConfigSnapshot baseline) {
        if (baseline == null) return Set.of();
        Set<String> changed = new LinkedHashSet<>();
        for (String key : RESTART_REQUIRED_KEYS) {
            Object before = baseline.values().get(key);
            Object after = candidate.values().get(key);
            if (!Objects.equals(before, after)) changed.add(key);
        }
        return Collections.unmodifiableSet(changed);
    }

    private void logDeprecatedKeys(ConfigSnapshot candidate) {
        for (String key : DEPRECATED_KEYS) {
            if (!candidate.values().containsKey(key) || !loggedDeprecations.add(key)) continue;
            plugin.getLogger().warning("Deprecated PlexonCore config key is ignored and may be removed: " + key);
        }
    }

    private void copyBundled(String resource, Path target) throws IOException {
        try (InputStream input = plugin.getResource(resource)) {
            if (input == null) throw new IOException("Bundled resource missing: " + resource);
            Files.copy(input, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    @FunctionalInterface
    public interface ConfigValidator {
        ValidationResult validate(YamlConfiguration candidate);
    }

    public interface ConfigMigration {
        int fromVersion();
        int toVersion();
        void apply(YamlConfiguration candidate) throws Exception;
    }

    public record MigrationResult(boolean success, int fromVersion, int toVersion, Path backup, String detail) {}
    public record LoadResult(ConfigSnapshot snapshot, ValidationResult validation) {}

    public record ValidationResult(boolean valid, String path, String reason) {
        public static ValidationResult ok() { return new ValidationResult(true, "", ""); }
        public static ValidationResult fail(String path, String reason) {
            return new ValidationResult(false, path == null ? "" : path, reason == null ? "Unknown validation failure" : reason);
        }
    }

    public static final class ConfigSnapshot {
        private final Map<String, Object> values;

        private ConfigSnapshot(Map<String, Object> values) {
            this.values = Collections.unmodifiableMap(values);
        }

        public static ConfigSnapshot empty() { return new ConfigSnapshot(Map.of()); }

        public static ConfigSnapshot from(YamlConfiguration yaml) {
            Map<String, Object> copy = new LinkedHashMap<>();
            yaml.getValues(true).forEach((key, value) -> {
                if (!(value instanceof org.bukkit.configuration.ConfigurationSection)) copy.put(key, value);
            });
            return new ConfigSnapshot(copy);
        }

        public Optional<String> string(String path) {
            Object value = values.get(path);
            return value == null ? Optional.empty() : Optional.of(String.valueOf(value));
        }

        public String string(String path, String fallback) { return string(path).orElse(fallback); }
        public int integer(String path, int fallback) {
            Object value = values.get(path);
            if (value instanceof Number number) return number.intValue();
            try { return value == null ? fallback : Integer.parseInt(String.valueOf(value)); }
            catch (NumberFormatException ignored) { return fallback; }
        }
        public boolean bool(String path, boolean fallback) {
            Object value = values.get(path);
            return value instanceof Boolean bool ? bool : value == null ? fallback : Boolean.parseBoolean(String.valueOf(value));
        }
        public Map<String, Object> values() { return values; }
    }
}
