package com.zpkdxgames.plexoncore.module;

import com.zpkdxgames.plexoncore.api.PlexonCoreAPI.CoreVersion;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;

import java.time.Instant;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class ModuleRegistry {
    private final CoreVersion coreVersion;
    private final List<CoreVersion> compatibilityVersions;
    private final Map<String, ModuleDescriptor> registered = new ConcurrentHashMap<>();
    private final Map<String, LegacyModule> legacy = new ConcurrentHashMap<>();

    public ModuleRegistry(CoreVersion coreVersion) {
        this(coreVersion, List.of());
    }

    public ModuleRegistry(CoreVersion coreVersion, Collection<CoreVersion> compatibilityVersions) {
        this.coreVersion = Objects.requireNonNull(coreVersion);
        this.compatibilityVersions = List.copyOf(compatibilityVersions == null ? List.of() : compatibilityVersions);
    }

    public RegistrationResult register(ModuleDescriptor descriptor) {
        Objects.requireNonNull(descriptor, "descriptor");
        String id = normalizeId(descriptor.id());
        ModuleDescriptor normalized = descriptor.withId(id);
        boolean compatible = isSupported(normalized.supportedCoreApi());
        ModuleDescriptor candidate = compatible ? normalized : normalized.withState(ModuleState.INCOMPATIBLE,
            "Requires Core API " + normalized.supportedCoreApi() + ", running " + coreVersion.apiVersion() + compatibilityDetail());

        while (true) {
            ModuleDescriptor previous = registered.putIfAbsent(id, candidate);
            if (previous == null) {
                legacy.remove(normalized.pluginName().toLowerCase(Locale.ROOT));
                return new RegistrationResult(compatible, candidate, compatible ? "Registered" : "Core API range is incompatible");
            }

            // A disabled owner must never block a fresh plugin instance from reclaiming its module id.
            // This is a safety net in addition to PluginDisableEvent cleanup and protects hot-enable flows.
            if (!previous.plugin().isEnabled() && registered.replace(id, previous, candidate)) {
                legacy.remove(normalized.pluginName().toLowerCase(Locale.ROOT));
                return new RegistrationResult(compatible, candidate,
                    compatible ? "Replaced stale disabled owner" : "Core API range is incompatible");
            }

            // Never let a duplicate registration, including an incompatible one, overwrite a live owner.
            return new RegistrationResult(false, previous, "Module id already registered: " + id);
        }
    }

    public boolean supportsApi(CoreVersion requested) {
        if (requested == null) return false;
        if (requested.apiMajor() == coreVersion.apiMajor() && requested.apiMinor() <= coreVersion.apiMinor()) return true;
        return compatibilityVersions.stream().anyMatch(version -> version.apiMajor() == requested.apiMajor() && requested.apiMinor() <= version.apiMinor());
    }

    public List<CoreVersion> compatibilityVersions() { return compatibilityVersions; }

    private boolean isSupported(ModuleVersionRange range) {
        if (range.contains(coreVersion)) return true;
        return compatibilityVersions.stream().anyMatch(range::contains);
    }

    private String compatibilityDetail() {
        if (compatibilityVersions.isEmpty()) return "";
        return ", compatibility " + compatibilityVersions.stream().map(CoreVersion::apiVersion).sorted().toList();
    }

    public Optional<ModuleDescriptor> unregister(String id) { return Optional.ofNullable(registered.remove(normalizeId(id))); }

    /**
     * Removes every module descriptor owned by the exact plugin instance.
     * Identity comparison is intentional: after a hot re-enable, an old plugin instance must not
     * be able to remove a descriptor registered by the new instance with the same plugin name.
     */
    public int unregisterOwnedBy(Plugin owner) {
        if (owner == null) return 0;
        int[] removed = {0};
        registered.forEach((id, descriptor) -> {
            if (descriptor.plugin() == owner && registered.remove(id, descriptor)) removed[0]++;
        });
        legacy.remove(owner.getName().toLowerCase(Locale.ROOT));
        return removed[0];
    }

    /**
     * Compatibility state update. First-party modules should prefer the ownership-aware overload
     * whenever state can be completed from asynchronous initialization.
     */
    public void updateState(String id, ModuleState state, String detail) {
        registered.computeIfPresent(normalizeId(id), (key, current) -> current.withState(state, detail));
    }

    /**
     * Updates state only when the current module descriptor is still owned by the supplied plugin
     * instance. This prevents a late callback from an old/disabled instance mutating a replacement
     * module after hot enable or reload.
     */
    public boolean updateState(String id, Plugin owner, ModuleState state, String detail) {
        Objects.requireNonNull(owner, "owner");
        String normalizedId = normalizeId(id);
        while (true) {
            ModuleDescriptor current = registered.get(normalizedId);
            if (current == null || current.plugin() != owner) return false;
            ModuleDescriptor updated = current.withState(state, detail);
            if (registered.replace(normalizedId, current, updated)) return true;
        }
    }

    public Optional<ModuleDescriptor> find(String id) { return Optional.ofNullable(registered.get(normalizeId(id))); }
    public Collection<ModuleDescriptor> registeredModules() { return registered.values().stream().sorted(Comparator.comparing(ModuleDescriptor::displayName)).toList(); }
    public Collection<LegacyModule> legacyModules() { return legacy.values().stream().sorted(Comparator.comparing(LegacyModule::pluginName)).toList(); }

    public void discoverLegacy(PluginManager pluginManager) {
        Set<String> installedAndEnabled = new LinkedHashSet<>();
        for (Plugin plugin : pluginManager.getPlugins()) {
            String name = plugin.getName();
            if (!name.toLowerCase(Locale.ROOT).startsWith("plexon") || name.equalsIgnoreCase("PlexonCore")) continue;
            String key = name.toLowerCase(Locale.ROOT);

            // Disabled plugins are not active legacy modules. Their registered descriptors are
            // removed on PluginDisableEvent so diagnostics cannot retain a stale READY state.
            if (!plugin.isEnabled()) {
                legacy.remove(key);
                continue;
            }

            installedAndEnabled.add(key);
            boolean isRegistered = registered.values().stream().anyMatch(d -> d.pluginName().equalsIgnoreCase(name));
            if (!isRegistered) legacy.put(key, new LegacyModule(name, plugin.getPluginMeta().getVersion(), true, Instant.now()));
            else legacy.remove(key);
        }
        legacy.keySet().removeIf(key -> !installedAndEnabled.contains(key));
    }

    public void clear() {
        registered.clear();
        legacy.clear();
    }

    public int totalDetected() { return registered.size() + legacy.size(); }
    public CoreVersion coreVersion() { return coreVersion; }

    private static String normalizeId(String id) {
        String normalized = Objects.requireNonNull(id, "id").trim().toLowerCase(Locale.ROOT).replace(' ', '-');
        if (!normalized.matches("[a-z0-9._-]{1,64}")) throw new IllegalArgumentException("Invalid module id: " + id);
        return normalized;
    }

    public enum ModuleState { DISCOVERED, STARTING, READY, DEGRADED, INCOMPATIBLE, DISABLED, FAILED, MISSING }

    public record ModuleDescriptor(String id, String displayName, String pluginName, String version, Plugin plugin,
                                   ModuleVersionRange supportedCoreApi, Set<String> capabilities, ModuleState state,
                                   String detail, Instant lastHealthUpdate) {
        public ModuleDescriptor {
            Objects.requireNonNull(id); Objects.requireNonNull(displayName); Objects.requireNonNull(pluginName);
            version = version == null ? "unknown" : version; Objects.requireNonNull(plugin);
            supportedCoreApi = supportedCoreApi == null ? ModuleVersionRange.anyMajor(1) : supportedCoreApi;
            capabilities = Collections.unmodifiableSet(new LinkedHashSet<>(capabilities == null ? Set.of() : capabilities));
            state = state == null ? ModuleState.DISCOVERED : state;
            detail = detail == null ? "" : detail;
            lastHealthUpdate = lastHealthUpdate == null ? Instant.now() : lastHealthUpdate;
        }
        ModuleDescriptor withId(String newId) { return new ModuleDescriptor(newId, displayName, pluginName, version, plugin, supportedCoreApi, capabilities, state, detail, lastHealthUpdate); }
        ModuleDescriptor withState(ModuleState newState, String newDetail) { return new ModuleDescriptor(id, displayName, pluginName, version, plugin, supportedCoreApi, capabilities, Objects.requireNonNull(newState), newDetail, Instant.now()); }
    }

    public record LegacyModule(String pluginName, String version, boolean enabled, Instant detectedAt) {}
    public record RegistrationResult(boolean success, ModuleDescriptor descriptor, String message) {}

    public record ModuleVersionRange(int minMajor, int minMinor, boolean minInclusive, int maxMajor, int maxMinor, boolean maxInclusive) {
        public static ModuleVersionRange anyMajor(int major) { return new ModuleVersionRange(major, 0, true, major + 1, 0, false); }
        public static ModuleVersionRange parse(String expression) {
            if (expression == null || expression.isBlank()) return anyMajor(1);
            int minMajor = 0, minMinor = 0, maxMajor = Integer.MAX_VALUE, maxMinor = Integer.MAX_VALUE;
            boolean minInclusive = true, maxInclusive = true;
            for (String token : expression.trim().split("\\s+")) {
                if (token.isBlank()) continue;
                String op;
                if (token.startsWith(">=")) op = ">="; else if (token.startsWith("<=")) op = "<="; else if (token.startsWith(">")) op = ">";
                else if (token.startsWith("<")) op = "<"; else if (token.startsWith("=")) op = "="; else op = "=";
                String value = token.substring(op.equals("=") && !token.startsWith("=") ? 0 : op.length());
                String[] parts = value.split("\\.", 3); int major = Integer.parseInt(parts[0]); int minor = parts.length > 1 ? Integer.parseInt(parts[1]) : 0;
                switch (op) {
                    case ">=" -> { minMajor = major; minMinor = minor; minInclusive = true; }
                    case ">" -> { minMajor = major; minMinor = minor; minInclusive = false; }
                    case "<=" -> { maxMajor = major; maxMinor = minor; maxInclusive = true; }
                    case "<" -> { maxMajor = major; maxMinor = minor; maxInclusive = false; }
                    default -> { minMajor = maxMajor = major; minMinor = maxMinor = minor; minInclusive = maxInclusive = true; }
                }
            }
            return new ModuleVersionRange(minMajor, minMinor, minInclusive, maxMajor, maxMinor, maxInclusive);
        }
        public boolean contains(CoreVersion version) {
            int lower = compare(version.apiMajor(), version.apiMinor(), minMajor, minMinor);
            int upper = compare(version.apiMajor(), version.apiMinor(), maxMajor, maxMinor);
            return (minInclusive ? lower >= 0 : lower > 0) && (maxInclusive ? upper <= 0 : upper < 0);
        }
        private static int compare(int aMajor, int aMinor, int bMajor, int bMinor) { int major = Integer.compare(aMajor, bMajor); return major != 0 ? major : Integer.compare(aMinor, bMinor); }
        @Override public String toString() { String lower = (minInclusive ? ">=" : ">") + minMajor + "." + minMinor; String upper = maxMajor == Integer.MAX_VALUE ? "" : " " + (maxInclusive ? "<=" : "<") + maxMajor + "." + maxMinor; return lower + upper; }
    }
}
