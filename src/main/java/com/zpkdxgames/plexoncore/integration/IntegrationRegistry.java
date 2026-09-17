package com.zpkdxgames.plexoncore.integration;

import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;

import java.time.Instant;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class IntegrationRegistry {
    private final PluginManager pluginManager;
    private final Map<String, IntegrationView> integrations = new ConcurrentHashMap<>();
    private final Map<String, IntegrationStatus> statuses = new ConcurrentHashMap<>();
    private final Map<String, String> knownPlugins = new LinkedHashMap<>();

    public IntegrationRegistry(PluginManager pluginManager) {
        this.pluginManager = Objects.requireNonNull(pluginManager);
        registerKnown("PLACEHOLDERAPI", "PlaceholderAPI");
        registerKnown("VAULT", "Vault");
        registerKnown("LUCKPERMS", "LuckPerms");
        registerKnown("PLEXON_RANKS", "PlexonRanks");
        registerKnown("PLEXON_QUESTS", "PlexonQuests");
        registerKnown("PLEXON_TOOLS", "PlexonTools");
        registerKnown("PLEXON_KEYS", "PlexonKeys");
        registerKnown("PLEXON_SHOPS", "PlexonShops");
        registerKnown("PLEXON_SPAWNERS", "PlexonSpawners");
        registerKnown("PLEXON_BLACKSMITH", "PlexonBlacksmith");
        registerKnown("PLEXON_CHATS", "PlexonChats");
        registerKnown("PLEXON_PANEL", "PlexonPanel");
        registerKnown("PLEXON_BACKPACKS", "PlexonBackpacks");
        registerKnown("PLEXON_GP_FLAGS", "PlexonGPFlags");
        // PlexonCrates is intentionally not an active Core integration in the 2.1 line.
    }

    public synchronized void registerKnown(String id, String pluginName) {
        knownPlugins.put(normalize(id), Objects.requireNonNull(pluginName));
    }

    public void refresh() {
        knownPlugins.forEach((id, pluginName) -> {
            Plugin plugin = pluginManager.getPlugin(pluginName);
            Presence presence = plugin == null ? Presence.MISSING : plugin.isEnabled() ? Presence.ENABLED : Presence.DISABLED;
            ProviderAvailability providerAvailability = plugin != null && plugin.isEnabled()
                    ? ProviderAvailability.AVAILABLE : ProviderAvailability.UNAVAILABLE;
            String version = plugin == null ? "-" : plugin.getPluginMeta().getVersion();
            IntegrationState legacyState = plugin == null ? IntegrationState.MISSING
                    : plugin.isEnabled() ? IntegrationState.READY : IntegrationState.DEGRADED;
            String detail = plugin == null ? "Not installed" : plugin.isEnabled() ? "Plugin present and enabled" : "Installed but disabled";
            IntegrationView previous = integrations.get(id);
            Set<String> capabilities = previous == null ? Set.of() : previous.capabilities();
            integrations.put(id, new IntegrationView(id, pluginName, version, legacyState, capabilities, detail, Instant.now()));
            statuses.put(id, new IntegrationStatus(id, presence, providerAvailability,
                    Compatibility.UNKNOWN, capabilities, detail, Instant.now()));
        });
    }

    /** Compatibility publishing API retained from API 2.0. */
    public void publish(String id, String provider, String version, IntegrationState state, Set<String> capabilities, String detail) {
        String normalized = normalize(id);
        IntegrationState normalizedState = state == null ? IntegrationState.DEGRADED : state;
        Set<String> safeCapabilities = capabilities == null ? Set.of() : Set.copyOf(capabilities);
        integrations.put(normalized, new IntegrationView(normalized, provider, version, normalizedState, safeCapabilities, detail, Instant.now()));
        statuses.put(normalized, new IntegrationStatus(normalized,
                normalizedState == IntegrationState.MISSING ? Presence.MISSING : Presence.ENABLED,
                normalizedState == IntegrationState.READY ? ProviderAvailability.AVAILABLE : ProviderAvailability.UNAVAILABLE,
                normalizedState == IntegrationState.INCOMPATIBLE ? Compatibility.INCOMPATIBLE
                        : normalizedState == IntegrationState.READY ? Compatibility.COMPATIBLE : Compatibility.UNKNOWN,
                safeCapabilities, detail, Instant.now()));
    }

    /** API 2.1 publishing surface for providers that can distinguish lifecycle dimensions. */
    public void publishStatus(String id, String provider, String version, Presence presence,
                              ProviderAvailability providerAvailability, Compatibility compatibility,
                              Set<String> capabilities, String detail) {
        String normalized = normalize(id);
        Set<String> safeCapabilities = capabilities == null ? Set.of() : Set.copyOf(capabilities);
        Presence safePresence = presence == null ? Presence.UNKNOWN : presence;
        ProviderAvailability safeProvider = providerAvailability == null ? ProviderAvailability.UNKNOWN : providerAvailability;
        Compatibility safeCompatibility = compatibility == null ? Compatibility.UNKNOWN : compatibility;
        IntegrationState legacyState = deriveLegacyState(safePresence, safeProvider, safeCompatibility);
        Instant now = Instant.now();
        integrations.put(normalized, new IntegrationView(normalized, provider, version, legacyState, safeCapabilities, detail, now));
        statuses.put(normalized, new IntegrationStatus(normalized, safePresence, safeProvider, safeCompatibility, safeCapabilities, detail, now));
    }

    public Optional<IntegrationView> get(String id) {
        return Optional.ofNullable(integrations.get(normalize(id)));
    }

    public Optional<IntegrationStatus> status(String id) {
        return Optional.ofNullable(statuses.get(normalize(id)));
    }

    public boolean ready(String id) {
        return get(id).map(view -> view.state() == IntegrationState.READY).orElse(false);
    }

    public boolean hasCapability(String id, String capability) {
        if (capability == null || capability.isBlank()) return false;
        String normalizedCapability = capability.trim().toLowerCase(Locale.ROOT);
        return status(id).filter(view -> view.providerAvailability() == ProviderAvailability.AVAILABLE)
                .map(view -> view.capabilities().stream().map(value -> value.toLowerCase(Locale.ROOT)).anyMatch(normalizedCapability::equals))
                .orElse(false);
    }

    public Collection<IntegrationView> all() {
        return integrations.values().stream().sorted(Comparator.comparing(IntegrationView::id)).toList();
    }

    public Collection<IntegrationStatus> allStatuses() {
        return statuses.values().stream().sorted(Comparator.comparing(IntegrationStatus::id)).toList();
    }

    private static IntegrationState deriveLegacyState(Presence presence, ProviderAvailability provider, Compatibility compatibility) {
        if (presence == Presence.MISSING) return IntegrationState.MISSING;
        if (compatibility == Compatibility.INCOMPATIBLE) return IntegrationState.INCOMPATIBLE;
        if (provider == ProviderAvailability.AVAILABLE && compatibility != Compatibility.INCOMPATIBLE) return IntegrationState.READY;
        return IntegrationState.DEGRADED;
    }

    private static String normalize(String id) {
        return Objects.requireNonNull(id, "id").trim().toUpperCase(Locale.ROOT).replace('-', '_').replace(' ', '_');
    }

    public enum IntegrationState { READY, DEGRADED, INCOMPATIBLE, MISSING, FAILED }
    public enum Presence { ENABLED, DISABLED, MISSING, UNKNOWN }
    public enum ProviderAvailability { AVAILABLE, UNAVAILABLE, UNKNOWN }
    public enum Compatibility { COMPATIBLE, INCOMPATIBLE, UNKNOWN }

    public record IntegrationStatus(
            String id,
            Presence presence,
            ProviderAvailability providerAvailability,
            Compatibility compatibility,
            Set<String> capabilities,
            String detail,
            Instant updatedAt) {
        public IntegrationStatus {
            Objects.requireNonNull(id);
            presence = presence == null ? Presence.UNKNOWN : presence;
            providerAvailability = providerAvailability == null ? ProviderAvailability.UNKNOWN : providerAvailability;
            compatibility = compatibility == null ? Compatibility.UNKNOWN : compatibility;
            capabilities = capabilities == null ? Set.of() : Set.copyOf(capabilities);
            detail = detail == null ? "" : detail;
            updatedAt = updatedAt == null ? Instant.now() : updatedAt;
        }
    }

    public record IntegrationView(
        String id,
        String provider,
        String version,
        IntegrationState state,
        Set<String> capabilities,
        String detail,
        Instant updatedAt
    ) {
        public IntegrationView {
            provider = provider == null ? "unknown" : provider;
            version = version == null ? "unknown" : version;
            capabilities = capabilities == null ? Set.of() : Set.copyOf(capabilities);
            detail = detail == null ? "" : detail;
            updatedAt = updatedAt == null ? Instant.now() : updatedAt;
        }
    }
}
