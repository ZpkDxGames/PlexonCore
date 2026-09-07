package com.zpkdxgames.plexoncore.module;

import com.zpkdxgames.plexoncore.api.PlexonCoreAPI.CoreVersion;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;

import java.time.Instant;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class ModuleRegistry {
    private final CoreVersion coreVersion;
    private final Map<String, ModuleDescriptor> registered = new ConcurrentHashMap<>();
    private final Map<String, LegacyModule> legacy = new ConcurrentHashMap<>();

    public ModuleRegistry(CoreVersion coreVersion) {
        this.coreVersion = Objects.requireNonNull(coreVersion);
    }

    public RegistrationResult register(ModuleDescriptor descriptor) {
        Objects.requireNonNull(descriptor, "descriptor");
        String id = normalizeId(descriptor.id());
        ModuleDescriptor normalized = descriptor.withId(id);
        if (!normalized.supportedCoreApi().contains(coreVersion)) {
            ModuleDescriptor incompatible = normalized.withState(ModuleState.INCOMPATIBLE,
                "Requires Core API " + normalized.supportedCoreApi() + ", running " + coreVersion.apiVersion());
            registered.put(id, incompatible);
            legacy.remove(normalized.pluginName().toLowerCase(Locale.ROOT));
            return new RegistrationResult(false, incompatible, "Core API range is incompatible");
        }
        ModuleDescriptor previous = registered.putIfAbsent(id, normalized);
        if (previous != null) {
            return new RegistrationResult(false, previous, "Module id already registered: " + id);
        }
        legacy.remove(normalized.pluginName().toLowerCase(Locale.ROOT));
        return new RegistrationResult(true, normalized, "Registered");
    }

    public Optional<ModuleDescriptor> unregister(String id) {
        return Optional.ofNullable(registered.remove(normalizeId(id)));
    }

    public void updateState(String id, ModuleState state, String detail) {
        registered.computeIfPresent(normalizeId(id), (key, current) -> current.withState(state, detail));
    }

    public Optional<ModuleDescriptor> find(String id) {
        return Optional.ofNullable(registered.get(normalizeId(id)));
    }

    public Collection<ModuleDescriptor> registeredModules() {
        return registered.values().stream().sorted(Comparator.comparing(ModuleDescriptor::displayName)).toList();
    }

    public Collection<LegacyModule> legacyModules() {
        return legacy.values().stream().sorted(Comparator.comparing(LegacyModule::pluginName)).toList();
    }

    public void discoverLegacy(PluginManager pluginManager) {
        Set<String> installed = new LinkedHashSet<>();
        for (Plugin plugin : pluginManager.getPlugins()) {
            String name = plugin.getName();
            if (!name.toLowerCase(Locale.ROOT).startsWith("plexon") || name.equalsIgnoreCase("PlexonCore")) continue;
            installed.add(name.toLowerCase(Locale.ROOT));
            boolean isRegistered = registered.values().stream().anyMatch(d -> d.pluginName().equalsIgnoreCase(name));
            if (!isRegistered) {
                legacy.put(name.toLowerCase(Locale.ROOT), new LegacyModule(name, plugin.getPluginMeta().getVersion(), plugin.isEnabled(), Instant.now()));
            }
        }
        legacy.keySet().removeIf(key -> !installed.contains(key));
    }

    public int totalDetected() {
        return registered.size() + legacy.size();
    }

    public CoreVersion coreVersion() {
        return coreVersion;
    }

    private static String normalizeId(String id) {
        String normalized = Objects.requireNonNull(id, "id").trim().toLowerCase(Locale.ROOT).replace(' ', '-');
        if (!normalized.matches("[a-z0-9._-]{1,64}")) throw new IllegalArgumentException("Invalid module id: " + id);
        return normalized;
    }

    public enum ModuleState {
        DISCOVERED, STARTING, READY, DEGRADED, INCOMPATIBLE, DISABLED, FAILED, MISSING
    }

    public record ModuleDescriptor(
        String id,
        String displayName,
        String pluginName,
        String version,
        Plugin plugin,
        ModuleVersionRange supportedCoreApi,
        Set<String> capabilities,
        ModuleState state,
        String detail,
        Instant lastHealthUpdate
    ) {
        public ModuleDescriptor {
            Objects.requireNonNull(id);
            Objects.requireNonNull(displayName);
            Objects.requireNonNull(pluginName);
            version = version == null ? "unknown" : version;
            Objects.requireNonNull(plugin);
            supportedCoreApi = supportedCoreApi == null ? ModuleVersionRange.anyMajor(1) : supportedCoreApi;
            capabilities = Collections.unmodifiableSet(new LinkedHashSet<>(capabilities == null ? Set.of() : capabilities));
            state = state == null ? ModuleState.DISCOVERED : state;
            detail = detail == null ? "" : detail;
            lastHealthUpdate = lastHealthUpdate == null ? Instant.now() : lastHealthUpdate;
        }

        ModuleDescriptor withId(String newId) {
            return new ModuleDescriptor(newId, displayName, pluginName, version, plugin, supportedCoreApi, capabilities, state, detail, lastHealthUpdate);
        }

        ModuleDescriptor withState(ModuleState newState, String newDetail) {
            return new ModuleDescriptor(id, displayName, pluginName, version, plugin, supportedCoreApi, capabilities,
                Objects.requireNonNull(newState), newDetail, Instant.now());
        }
    }

    public record LegacyModule(String pluginName, String version, boolean enabled, Instant detectedAt) {}

    public record RegistrationResult(boolean success, ModuleDescriptor descriptor, String message) {}

    public record ModuleVersionRange(int minMajor, int minMinor, boolean minInclusive, int maxMajor, int maxMinor, boolean maxInclusive) {
        public static ModuleVersionRange anyMajor(int major) {
            return new ModuleVersionRange(major, 0, true, major + 1, 0, false);
        }

        public static ModuleVersionRange parse(String expression) {
            if (expression == null || expression.isBlank()) return anyMajor(1);
            int minMajor = 0, minMinor = 0, maxMajor = Integer.MAX_VALUE, maxMinor = Integer.MAX_VALUE;
            boolean minInclusive = true, maxInclusive = true;
            for (String token : expression.trim().split("\\s+")) {
                if (token.isBlank()) continue;
                String op;
                if (token.startsWith(">=")) op = ">=";
                else if (token.startsWith("<=")) op = "<=";
                else if (token.startsWith(">")) op = ">";
                else if (token.startsWith("<")) op = "<";
                else if (token.startsWith("=")) op = "=";
                else op = "=";
                String value = token.substring(op.equals("=") && !token.startsWith("=") ? 0 : op.length());
                String[] parts = value.split("\\.", 3);
                int major = Integer.parseInt(parts[0]);
                int minor = parts.length > 1 ? Integer.parseInt(parts[1]) : 0;
                switch (op) {
                    case ">=" -> { minMajor = major; minMinor = minor; minInclusive = true; }
                    case ">" -> { minMajor = major; minMinor = minor; minInclusive = false; }
                    case "<=" -> { maxMajor = major; maxMinor = minor; maxInclusive = true; }
                    case "<" -> { maxMajor = major; maxMinor = minor; maxInclusive = false; }
                    default -> {
                        minMajor = maxMajor = major;
                        minMinor = maxMinor = minor;
                        minInclusive = maxInclusive = true;
                    }
                }
            }
            return new ModuleVersionRange(minMajor, minMinor, minInclusive, maxMajor, maxMinor, maxInclusive);
        }

        public boolean contains(CoreVersion version) {
            int lower = compare(version.apiMajor(), version.apiMinor(), minMajor, minMinor);
            int upper = compare(version.apiMajor(), version.apiMinor(), maxMajor, maxMinor);
            boolean lowerOk = minInclusive ? lower >= 0 : lower > 0;
            boolean upperOk = maxInclusive ? upper <= 0 : upper < 0;
            return lowerOk && upperOk;
        }

        private static int compare(int aMajor, int aMinor, int bMajor, int bMinor) {
            int major = Integer.compare(aMajor, bMajor);
            return major != 0 ? major : Integer.compare(aMinor, bMinor);
        }

        @Override
        public String toString() {
            String lower = (minInclusive ? ">=" : ">") + minMajor + "." + minMinor;
            String upper = maxMajor == Integer.MAX_VALUE ? "" : " " + (maxInclusive ? "<=" : "<") + maxMajor + "." + maxMinor;
            return lower + upper;
        }
    }
}
