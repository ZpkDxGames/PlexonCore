package com.zpkdxgames.plexoncore;

import com.zpkdxgames.plexoncore.api.PlexonCoreAPI;
import com.zpkdxgames.plexoncore.api.PlexonCoreAPI.CoreVersion;
import com.zpkdxgames.plexoncore.config.ConfigService;
import com.zpkdxgames.plexoncore.diagnostics.DiagnosticsService;
import com.zpkdxgames.plexoncore.gui.GuiService;
import com.zpkdxgames.plexoncore.integration.IntegrationRegistry;
import com.zpkdxgames.plexoncore.item.ItemService;
import com.zpkdxgames.plexoncore.module.ModuleRegistry;
import com.zpkdxgames.plexoncore.persistence.SqliteService;
import com.zpkdxgames.plexoncore.scheduler.CoreScheduler;
import com.zpkdxgames.plexoncore.text.TextService;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.server.PluginDisableEvent;
import org.bukkit.event.server.PluginEnableEvent;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Objects;

public final class PlexonCorePlugin extends JavaPlugin implements Listener {
    private CoreVersion coreVersion;
    private TextService textService;
    private ConfigService configService;
    private CoreScheduler scheduler;
    private ModuleRegistry moduleRegistry;
    private IntegrationRegistry integrationRegistry;
    private GuiService guiService;
    private ItemService itemService;
    private SqliteService sqliteService;
    private DiagnosticsService diagnosticsService;
    private PlexonCoreAPI api;

    @Override
    public void onEnable() {
        coreVersion = CoreVersion.of(1, 0, getPluginMeta().getVersion());
        textService = new TextService(getServer().getPluginManager());
        configService = new ConfigService(this, textService);
        ConfigService.ValidationResult initialConfig = configService.initializeCore();

        int workers = configService.core().integer("executor.worker-threads", 2);
        int queueCapacity = configService.core().integer("executor.queue-capacity", 4096);
        scheduler = new CoreScheduler(this, workers, queueCapacity);
        moduleRegistry = new ModuleRegistry(coreVersion);
        integrationRegistry = new IntegrationRegistry(getServer().getPluginManager());
        guiService = new GuiService(this);
        itemService = new ItemService();
        sqliteService = new SqliteService(scheduler);
        diagnosticsService = new DiagnosticsService(this, coreVersion, moduleRegistry, integrationRegistry, configService, scheduler, guiService, textService);

        moduleRegistry.discoverLegacy(getServer().getPluginManager());
        integrationRegistry.refresh();
        if (!initialConfig.valid()) diagnosticsService.warning("Core config candidate rejected: " + initialConfig.path() + " — " + initialConfig.reason());

        api = new DefaultCoreAPI();
        getServer().getServicesManager().register(PlexonCoreAPI.class, api, this, ServicePriority.Normal);
        Objects.requireNonNull(getCommand("plexon"), "plexon command missing from plugin.yml").setExecutor(diagnosticsService);
        Objects.requireNonNull(getCommand("plexon"), "plexon command missing from plugin.yml").setTabCompleter(diagnosticsService);
        getServer().getPluginManager().registerEvents(this, this);

        getLogger().info("1.0.0 enabled");
        getLogger().info("API 1.0");
        getLogger().info("Modules discovered: " + moduleRegistry.totalDetected());
        String ready = integrationRegistry.all().stream().filter(i -> i.state() == IntegrationRegistry.IntegrationState.READY).map(IntegrationRegistry.IntegrationView::provider).sorted().reduce((a, b) -> a + ", " + b).orElse("none");
        getLogger().info("Integrations ready: " + ready);
    }

    @Override
    public void onDisable() {
        if (getServer() != null) getServer().getServicesManager().unregisterAll(this);
        if (sqliteService != null) sqliteService.close();
        if (scheduler != null) scheduler.close();
    }

    @EventHandler
    public void onPluginEnable(PluginEnableEvent event) { refreshDiscovery(event.getPlugin().getName()); }

    @EventHandler
    public void onPluginDisable(PluginDisableEvent event) { refreshDiscovery(event.getPlugin().getName()); }

    private void refreshDiscovery(String pluginName) {
        if (pluginName.equalsIgnoreCase(getName())) return;
        if (pluginName.toLowerCase(java.util.Locale.ROOT).startsWith("plexon") || pluginName.equalsIgnoreCase("PlaceholderAPI") || pluginName.equalsIgnoreCase("Vault") || pluginName.equalsIgnoreCase("LuckPerms")) {
            scheduler.runPrimary(() -> {
                moduleRegistry.discoverLegacy(Bukkit.getPluginManager());
                integrationRegistry.refresh();
            });
        }
    }

    private final class DefaultCoreAPI implements PlexonCoreAPI {
        @Override public CoreVersion version() { return coreVersion; }
        @Override public ModuleRegistry modules() { return moduleRegistry; }
        @Override public IntegrationRegistry integrations() { return integrationRegistry; }
        @Override public TextService text() { return textService; }
        @Override public GuiService gui() { return guiService; }
        @Override public ItemService items() { return itemService; }
        @Override public CoreScheduler scheduler() { return scheduler; }
        @Override public SqliteService persistence() { return sqliteService; }
        @Override public ConfigService configs() { return configService; }
        @Override public DiagnosticsService.DiagnosticsSnapshot diagnostics() { return diagnosticsService.snapshot(); }
    }
}
