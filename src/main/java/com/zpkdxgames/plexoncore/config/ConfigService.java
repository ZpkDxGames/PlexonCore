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
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

public final class ConfigService {
    private static final DateTimeFormatter BACKUP_STAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").withZone(ZoneOffset.UTC);

    private final JavaPlugin plugin;
    private final TextService textService;
    private final Path root;
    private final AtomicReference<ConfigSnapshot> coreSnapshot = new AtomicReference<>(ConfigSnapshot.empty());
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
            return reloadCore();
        } catch (Exception ex) {
            failedReloads++;
            lastValidation = ValidationResult.fail("config.yml", ex.getMessage());
            return lastValidation;
        }
    }

    public synchronized ValidationResult reloadCore() {
        try {
            Path config = resolveSafe("config.yml");
            LoadResult result = loadCandidate(config, List.of(this::validateCoreSchema, this::validateConfiguredMiniMessage));
            if (!result.validation().valid()) {
                failedReloads++;
                lastValidation = result.validation();
                return result.validation();
            }
            coreSnapshot.set(result.snapshot());
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
            if (current >= targetVersion) return new MigrationResult(true, current, current, null, "Already current");
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
        if (workers < 1 || workers > 32) return ValidationResult.fail("executor.worker-threads", "Must be between 1 and 32");
        if (queue < 64 || queue > 100_000) return ValidationResult.fail("executor.queue-capacity", "Must be between 64 and 100000");
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
