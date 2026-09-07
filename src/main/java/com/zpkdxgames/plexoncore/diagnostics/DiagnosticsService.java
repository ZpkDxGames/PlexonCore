package com.zpkdxgames.plexoncore.diagnostics;

import com.zpkdxgames.plexoncore.api.PlexonCoreAPI.CoreVersion;
import com.zpkdxgames.plexoncore.config.ConfigService;
import com.zpkdxgames.plexoncore.gui.GuiService;
import com.zpkdxgames.plexoncore.integration.IntegrationRegistry;
import com.zpkdxgames.plexoncore.integration.IntegrationRegistry.IntegrationState;
import com.zpkdxgames.plexoncore.module.ModuleRegistry;
import com.zpkdxgames.plexoncore.module.ModuleRegistry.ModuleState;
import com.zpkdxgames.plexoncore.scheduler.CoreScheduler;
import com.zpkdxgames.plexoncore.text.TextService;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.Plugin;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

public final class DiagnosticsService implements CommandExecutor, TabCompleter {
    private static final String PREFIX = "<gradient:#88beff:#b9d8ff><bold>PlexonCore</bold></gradient> <dark_gray>»</dark_gray> ";

    private final Plugin plugin;
    private final CoreVersion version;
    private final ModuleRegistry modules;
    private final IntegrationRegistry integrations;
    private final ConfigService configs;
    private final CoreScheduler scheduler;
    private final GuiService gui;
    private final TextService text;
    private final List<String> startupWarnings = new java.util.concurrent.CopyOnWriteArrayList<>();

    public DiagnosticsService(Plugin plugin, CoreVersion version, ModuleRegistry modules, IntegrationRegistry integrations,
                              ConfigService configs, CoreScheduler scheduler, GuiService gui, TextService text) {
        this.plugin = Objects.requireNonNull(plugin);
        this.version = Objects.requireNonNull(version);
        this.modules = Objects.requireNonNull(modules);
        this.integrations = Objects.requireNonNull(integrations);
        this.configs = Objects.requireNonNull(configs);
        this.scheduler = Objects.requireNonNull(scheduler);
        this.gui = Objects.requireNonNull(gui);
        this.text = Objects.requireNonNull(text);
    }

    public DiagnosticsSnapshot snapshot() {
        HealthState state = HealthState.HEALTHY;
        if (!configs.lastValidation().valid()) state = HealthState.DEGRADED;
        if (modules.registeredModules().stream().anyMatch(m -> m.state() == ModuleState.FAILED)) state = HealthState.DEGRADED;
        if (!startupWarnings.isEmpty()) state = HealthState.DEGRADED;
        return new DiagnosticsSnapshot(
            version.pluginVersion(), version.apiVersion(), Bukkit.getVersion(), System.getProperty("java.version", "unknown"),
            state, modules.registeredModules().size(), modules.legacyModules().size(), integrations.all().size(),
            gui.activeSessions(), scheduler.workerCount(), scheduler.activeWorkers(), scheduler.queueSize(),
            configs.lastValidation().valid(), configs.successfulReloads(), configs.failedReloads(), List.copyOf(startupWarnings), Instant.now()
        );
    }

    public void warning(String warning) {
        if (warning != null && !warning.isBlank() && !startupWarnings.contains(warning)) startupWarnings.add(warning);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            if (sender instanceof Player player) openOverview(player);
            else sendStatus(sender);
            return true;
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        return switch (sub) {
            case "status" -> withPermission(sender, "plexoncore.admin.status", () -> sendStatus(sender));
            case "modules" -> withPermission(sender, "plexoncore.admin.modules", () -> sendModules(sender));
            case "integrations" -> withPermission(sender, "plexoncore.admin.integrations", () -> sendIntegrations(sender));
            case "diagnostics" -> withPermission(sender, "plexoncore.admin.diagnostics", () -> sendDiagnostics(sender));
            case "version" -> { sendVersion(sender); yield true; }
            case "reload" -> withPermission(sender, "plexoncore.admin.reload", () -> reload(sender));
            default -> { send(sender, "<gray>Usage: <white>/plexon [status|modules|integrations|diagnostics|version|reload]</white>"); yield true; }
        };
    }

    private void openOverview(Player player) {
        var builder = gui.builder("core", "ecosystem", text.render(TextService.TextMode.MINIMESSAGE,
            "<gradient:#88beff:#b9d8ff><bold>Plexon Ecosystem</bold></gradient>"), 6).filler(Material.GRAY_STAINED_GLASS_PANE);
        builder.button(4, icon(Material.NETHER_STAR, "<aqua><bold>PlexonCore</bold>",
            "<gray>Plugin:</gray> <white>" + version.pluginVersion() + "</white>",
            "<gray>API:</gray> <white>" + version.apiVersion() + "</white>",
            "<gray>Health:</gray> " + healthColor(snapshot().health()) + snapshot().health()), click -> {});

        int slot = 10;
        for (var module : modules.registeredModules()) {
            if (slot >= 44) break;
            builder.button(slot++, icon(materialFor(module.id()), "<white><bold>" + escape(module.displayName()) + "</bold>",
                "<gray>Version:</gray> <white>" + escape(module.version()) + "</white>",
                "<gray>Mode:</gray> <aqua>Core API</aqua>",
                "<gray>State:</gray> " + stateColor(module.state()) + module.state()), click -> {});
        }
        for (var legacy : modules.legacyModules()) {
            if (slot >= 44) break;
            builder.button(slot++, icon(materialFor(legacy.pluginName()), "<white><bold>" + escape(legacy.pluginName()) + "</bold>",
                "<gray>Version:</gray> <white>" + escape(legacy.version()) + "</white>",
                "<gray>Mode:</gray> <yellow>Legacy / Standalone</yellow>",
                "<gray>Enabled:</gray> " + (legacy.enabled() ? "<green>yes" : "<red>no")), click -> {});
        }
        builder.button(49, icon(Material.COMPARATOR, "<aqua><bold>Diagnostics</bold>",
            "<gray>Modules:</gray> <white>" + modules.totalDetected() + "</white>",
            "<gray>Integrations:</gray> <white>" + integrations.all().size() + "</white>",
            "<gray>Async queue:</gray> <white>" + scheduler.queueSize() + "</white>"), click -> sendDiagnostics(click.player()));
        builder.open(player);
    }

    private ItemStack icon(Material material, String name, String... lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(text.render(TextService.TextMode.MINIMESSAGE, name));
        List<Component> lines = new ArrayList<>();
        for (String line : lore) lines.add(text.render(TextService.TextMode.MINIMESSAGE, line));
        meta.lore(lines);
        item.setItemMeta(meta);
        return item;
    }

    private void sendStatus(CommandSender sender) {
        DiagnosticsSnapshot snap = snapshot();
        send(sender, "<gray>Core health:</gray> " + healthColor(snap.health()) + snap.health());
        send(sender, "<gray>Modules:</gray> <white>" + (snap.registeredModules() + snap.legacyModules()) + "</white> <dark_gray>(" + snap.registeredModules() + " Core API, " + snap.legacyModules() + " legacy)</dark_gray>");
        send(sender, "<gray>Config:</gray> " + (snap.configValid() ? "<green>valid" : "<red>invalid"));
    }

    private void sendModules(CommandSender sender) {
        if (modules.totalDetected() == 0) { send(sender, "<gray>No Plexon modules detected.</gray>"); return; }
        modules.registeredModules().forEach(module -> send(sender, "<aqua>" + escape(module.displayName()) + "</aqua> <dark_gray>—</dark_gray> " + stateColor(module.state()) + module.state() + " <dark_gray>| Core API | " + escape(module.version()) + "</dark_gray>"));
        modules.legacyModules().forEach(module -> send(sender, "<yellow>" + escape(module.pluginName()) + "</yellow> <dark_gray>— Legacy/Standalone | " + escape(module.version()) + "</dark_gray>"));
    }

    private void sendIntegrations(CommandSender sender) {
        integrations.all().forEach(integration -> send(sender, "<white>" + integration.id() + "</white> <dark_gray>—</dark_gray> " + integrationColor(integration.state()) + integration.state() + " <dark_gray>| " + escape(integration.provider()) + " " + escape(integration.version()) + "</dark_gray>"));
    }

    private void sendDiagnostics(CommandSender sender) {
        DiagnosticsSnapshot snap = snapshot();
        send(sender, "<gray>Plugin/API:</gray> <white>" + snap.pluginVersion() + " / " + snap.apiVersion() + "</white>");
        send(sender, "<gray>Paper:</gray> <white>" + escape(snap.paperVersion()) + "</white>");
        send(sender, "<gray>Java:</gray> <white>" + escape(snap.javaVersion()) + "</white>");
        send(sender, "<gray>Executor:</gray> <white>" + snap.activeWorkers() + "/" + snap.workerThreads() + " active, queue " + snap.executorQueue() + "</white>");
        send(sender, "<gray>GUI sessions:</gray> <white>" + snap.activeGuiSessions() + "</white>");
        send(sender, "<gray>Config reloads:</gray> <white>" + snap.successfulReloads() + " ok, " + snap.failedReloads() + " failed</white>");
        if (!snap.startupWarnings().isEmpty()) snap.startupWarnings().forEach(w -> send(sender, "<yellow>Warning:</yellow> <gray>" + escape(w) + "</gray>"));
    }

    private void sendVersion(CommandSender sender) {
        send(sender, "<gray>PlexonCore:</gray> <white>" + version.pluginVersion() + "</white> <dark_gray>|</dark_gray> <gray>API:</gray> <white>" + version.apiVersion() + "</white>");
    }

    private void reload(CommandSender sender) {
        scheduler.supplyAsync(configs::reloadCore).whenComplete((result, error) -> scheduler.runPrimary(() -> {
            if (error != null) { send(sender, "<red>Reload failed:</red> <gray>" + escape(error.getMessage()) + "</gray>"); return; }
            if (!result.valid()) { send(sender, "<red>Reload rejected:</red> <white>" + escape(result.path()) + "</white> <gray>" + escape(result.reason()) + "</gray>"); return; }
            modules.discoverLegacy(Bukkit.getPluginManager());
            integrations.refresh();
            send(sender, "<green>PlexonCore configuration reloaded atomically.</green>");
        }));
    }

    private boolean withPermission(CommandSender sender, String permission, Runnable action) {
        if (!sender.hasPermission(permission)) { send(sender, "<red>You do not have permission for that Core diagnostic.</red>"); return true; }
        action.run();
        return true;
    }

    private void send(CommandSender sender, String message) { sender.sendMessage(text.render(TextService.TextMode.MINIMESSAGE, PREFIX + message)); }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length != 1) return List.of();
        String prefix = args[0].toLowerCase(Locale.ROOT);
        return List.of("status", "modules", "integrations", "diagnostics", "version", "reload").stream().filter(s -> s.startsWith(prefix)).toList();
    }

    private static Material materialFor(String id) {
        String value = id.toLowerCase(Locale.ROOT);
        if (value.contains("quest")) return Material.BOOK;
        if (value.contains("rank")) return Material.NAME_TAG;
        if (value.contains("key")) return Material.TRIPWIRE_HOOK;
        if (value.contains("crate")) return Material.CHEST;
        if (value.contains("tool")) return Material.DIAMOND_PICKAXE;
        if (value.contains("blacksmith")) return Material.ANVIL;
        if (value.contains("shop")) return Material.EMERALD;
        if (value.contains("spawner")) return Material.SPAWNER;
        if (value.contains("chat")) return Material.PAPER;
        if (value.contains("backpack")) return Material.ENDER_CHEST;
        if (value.contains("panel")) return Material.COMPARATOR;
        return Material.ENDER_EYE;
    }

    private static String stateColor(ModuleState state) { return switch (state) { case READY -> "<green>"; case DEGRADED, STARTING, DISCOVERED -> "<yellow>"; default -> "<red>"; }; }
    private static String integrationColor(IntegrationState state) { return state == IntegrationState.READY ? "<green>" : state == IntegrationState.MISSING ? "<gray>" : "<yellow>"; }
    private static String healthColor(HealthState state) { return state == HealthState.HEALTHY ? "<green>" : state == HealthState.DEGRADED ? "<yellow>" : "<red>"; }
    private static String escape(String value) { return value == null ? "" : value.replace("<", "\\<").replace(">", "\\>"); }

    public enum HealthState { HEALTHY, DEGRADED, FAILED, UNKNOWN }

    public record DiagnosticsSnapshot(
        String pluginVersion, String apiVersion, String paperVersion, String javaVersion, HealthState health,
        int registeredModules, int legacyModules, int integrationCount, int activeGuiSessions,
        int workerThreads, int activeWorkers, int executorQueue, boolean configValid,
        long successfulReloads, long failedReloads, List<String> startupWarnings, Instant capturedAt
    ) {}
}
