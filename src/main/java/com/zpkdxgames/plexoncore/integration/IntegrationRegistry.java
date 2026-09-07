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
        registerKnown("PLEXON_CRATES", "PlexonCrates");
        registerKnown("PLEXON_SHOPS", "PlexonShops");
        registerKnown("PLEXON_SPAWNERS", "PlexonSpawners");
        registerKnown("PLEXON_BLACKSMITH", "PlexonBlacksmith");
        registerKnown("PLEXON_CHATS", "PlexonChats");
        registerKnown("PLEXON_PANEL", "PlexonPanel");
        registerKnown("PLEXON_BACKPACKS", "PlexonBackpacks");
        registerKnown("PLEXON_CLAIM_FLAGS", "PlexonClaimFlags");
    }

    public void registerKnown(String id, String pluginName) {
        knownPlugins.put(normalize(id), Objects.requireNonNull(pluginName));
    }

    public void refresh() {
        knownPlugins.forEach((id, pluginName) -> {
            Plugin plugin = pluginManager.getPlugin(pluginName);
            IntegrationState state = plugin == null ? IntegrationState.MISSING : (plugin.isEnabled() ? IntegrationState.READY : IntegrationState.DEGRADED);
            String version = plugin == null ? "-" : plugin.getPluginMeta().getVersion();
            String detail = plugin == null ? "Not installed" : (plugin.isEnabled() ? "Available" : "Installed but disabled");
            integrations.put(id, new IntegrationView(id, pluginName, version, state, Set.of(), detail, Instant.now()));
        });
    }

    public void publish(String id, String provider, String version, IntegrationState state, Set<String> capabilities, String detail) {
        integrations.put(normalize(id), new IntegrationView(normalize(id), provider, version, state, capabilities, detail, Instant.now()));
    }

    public Optional<IntegrationView> get(String id) {
        return Optional.ofNullable(integrations.get(normalize(id)));
    }

    public boolean ready(String id) {
        return get(id).map(view -> view.state() == IntegrationState.READY).orElse(false);
    }

    public Collection<IntegrationView> all() {
        return integrations.values().stream().sorted(Comparator.comparing(IntegrationView::id)).toList();
    }

    private static String normalize(String id) {
        return id.trim().toUpperCase(Locale.ROOT).replace('-', '_').replace(' ', '_');
    }

    public enum IntegrationState { READY, DEGRADED, INCOMPATIBLE, MISSING, FAILED }

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
