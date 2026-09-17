package com.zpkdxgames.plexoncore.diagnostics;

import com.zpkdxgames.plexoncore.api.PlexonCoreAPI.CoreVersion;
import com.zpkdxgames.plexoncore.config.ConfigService;
import com.zpkdxgames.plexoncore.diagnostics.HealthAggregator.Contributor;
import com.zpkdxgames.plexoncore.diagnostics.HealthAggregator.ContributorState;
import com.zpkdxgames.plexoncore.event.CoreEventGateway;
import com.zpkdxgames.plexoncore.gui.GuiService;
import com.zpkdxgames.plexoncore.integration.IntegrationRegistry;
import com.zpkdxgames.plexoncore.integration.IntegrationRegistry.IntegrationState;
import com.zpkdxgames.plexoncore.module.ModuleRegistry;
import com.zpkdxgames.plexoncore.module.ModuleRegistry.ModuleState;
import com.zpkdxgames.plexoncore.origin.BlockOriginService;
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
    private static final long CALLBACK_DEGRADED_WINDOW_NANOS = 60_000_000_000L;

    private final Plugin plugin;
    private final CoreVersion version;
    private final ModuleRegistry modules;
    private final IntegrationRegistry integrations;
    private final ConfigService configs;
    private final CoreScheduler scheduler;
    private final GuiService gui;
    private final TextService text;
    private final CoreEventGateway events;
    private final BlockOriginService origins;
    private final List<String> startupWarnings = new java.util.concurrent.CopyOnWriteArrayList<>();

    public DiagnosticsService(Plugin plugin, CoreVersion version, ModuleRegistry modules, IntegrationRegistry integrations,
                              ConfigService configs, CoreScheduler scheduler, GuiService gui, TextService text) {
        this(plugin, version, modules, integrations, configs, scheduler, gui, text, null, null);
    }

    public DiagnosticsService(Plugin plugin, CoreVersion version, ModuleRegistry modules, IntegrationRegistry integrations,
                              ConfigService configs, CoreScheduler scheduler, GuiService gui, TextService text,
                              CoreEventGateway events, BlockOriginService origins) {
        this.plugin = Objects.requireNonNull(plugin);
        this.version = Objects.requireNonNull(version);
        this.modules = Objects.requireNonNull(modules);
        this.integrations = Objects.requireNonNull(integrations);
        this.configs = Objects.requireNonNull(configs);
        this.scheduler = Objects.requireNonNull(scheduler);
        this.gui = Objects.requireNonNull(gui);
        this.text = Objects.requireNonNull(text);
        this.events = events;
        this.origins = origins;
    }

    public DiagnosticsSnapshot snapshot() {
        return detailedSnapshot().snapshot();
    }

    public DetailedDiagnostics detailedSnapshot() {
        HealthAggregator.Snapshot health = HealthAggregator.aggregate(healthContributors());
        HealthState legacyState = switch (health.state()) {
            case READY -> HealthState.HEALTHY;
            case DEGRADED -> HealthState.DEGRADED;
            case FAILED -> HealthState.FAILED;
        };
        DiagnosticsSnapshot snapshot = new DiagnosticsSnapshot(
                version.pluginVersion(), version.apiVersion(), Bukkit.getVersion(), System.getProperty("java.version", "unknown"),
                legacyState, modules.registeredModules().size(), modules.legacyModules().size(), integrations.all().size(),
                gui.activeSessions(), scheduler.workerCount(), scheduler.activeWorkers(), scheduler.queueSize(),
                configs.lastValidation().valid(), configs.successfulReloads(), configs.failedReloads(), List.copyOf(startupWarnings), Instant.now());
        return new DetailedDiagnostics(snapshot, health.contributors());
    }

    private List<Contributor> healthContributors() {
        List<Contributor> contributors = new ArrayList<>();
        contributors.add(new Contributor("config.validation",
                configs.lastValidation().valid() ? ContributorState.READY : ContributorState.DEGRADED,
                configs.lastValidation().valid() ? "Valid" : configs.lastValidation().path() + ": " + configs.lastValidation().reason()));
        contributors.add(new Contributor("config.restart-required",
                configs.restartRequired() ? ContributorState.DEGRADED : ContributorState.READY,
                configs.restartRequired() ? String.join(", ", configs.restartRequiredSettings()) : "None"));

        for (var module : modules.registeredModules()) {
            ContributorState state = switch (module.state()) {
                case READY -> ContributorState.READY;
                case INCOMPATIBLE -> ContributorState.INCOMPATIBLE;
                case FAILED -> ContributorState.FAILED;
                default -> ContributorState.DEGRADED;
            };
            contributors.add(new Contributor("module." + module.id(), state, module.detail()));
        }

        for (var integration : integrations.all()) {
            ContributorState state = switch (integration.state()) {
                case READY, MISSING -> ContributorState.READY; // known integrations are optional unless a module declares otherwise
                case DEGRADED -> ContributorState.DEGRADED;
                case INCOMPATIBLE -> ContributorState.INCOMPATIBLE;
                case FAILED -> ContributorState.FAILED;
            };
            contributors.add(new Contributor("integration." + integration.id().toLowerCase(Locale.ROOT), state, integration.detail()));
        }

        CoreScheduler.SchedulerHealth schedulerHealth = scheduler.health();
        contributors.add(new Contributor("scheduler",
                schedulerHealth.state() == CoreScheduler.HealthState.READY ? ContributorState.READY : ContributorState.DEGRADED,
                schedulerHealth.lastFailure() == null ? "Ready" : schedulerHealth.lastFailure()));

        if (origins != null) {
            var origin = origins.persistenceStats();
            ContributorState state = switch (origin.writerState()) {
                case READY -> ContributorState.READY;
                case STARTING, DEGRADED -> ContributorState.DEGRADED;
                case FAILED, CLOSED -> ContributorState.FAILED;
            };
            contributors.add(new Contributor("block-origin.persistence", state,
                    origin.lastFailure() == null ? origin.writerState().name() : origin.lastFailure()));
        }

        if (events != null) {
            long lastFailure = events.lastCallbackFailureNanos();
            boolean recentFailure = lastFailure > 0L && System.nanoTime() - lastFailure < CALLBACK_DEGRADED_WINDOW_NANOS;
            contributors.add(new Contributor("events.callbacks", recentFailure ? ContributorState.DEGRADED : ContributorState.READY,
                    recentFailure ? "Subscriber failure observed in the last 60 seconds" : "Ready"));
        }

        var papi = integrations.get("PLACEHOLDERAPI");
        var textProvider = text.placeholderProvider();
        boolean papiEnabled = papi.isPresent() && papi.get().state() == IntegrationState.READY;
        contributors.add(new Contributor("text.placeholderapi",
                papiEnabled && !textProvider.ready() ? ContributorState.DEGRADED : ContributorState.READY,
                textProvider.detail()));

        for (int i = 0; i < startupWarnings.size(); i++) {
            contributors.add(new Contributor("startup.warning." + i, ContributorState.DEGRADED, startupWarnings.get(i)));
        }
        return contributors;
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
            case "perf" -> withPermission(sender, "plexoncore.admin.diagnostics", () -> sendPerf(sender));
            case "version" -> { sendVersion(sender); yield true; }
            case "reload" -> withPermission(sender, "plexoncore.admin.reload", () -> reload(sender));
            default -> { send(sender, "<gray>Usage: <white>/plexon [status|modules|integrations|diagnostics|perf|version|reload]</white>"); yield true; }
        };
    }

    private void openOverview(Player player) {
        var builder = gui.builder(plugin, "core", "ecosystem", text.render(TextService.TextMode.MINIMESSAGE,
                "<gradient:#88beff:#b9d8ff><bold>Plexon Ecosystem</bold></gradient>"), 6)
                .filler(Material.GRAY_STAINED_GLASS_PANE);
        builder.button(4, icon(Material.NETHER_STAR, "<aqua><bold>PlexonCore</bold>",
                "<gray>Plugin:</gray> <white>" + version.pluginVersion() + "</white>",
                "<gray>API:</gray> <white>" + version.apiVersion() + "</white>",
                "<gray>Health:</gray> " + healthColor(snapshot().health()) + snapshot().health()), click -> {});
        int slot = 10;
        for (var module : modules.registeredModules()) {
            if (slot >= 44) break;
            builder.button(slot++, icon(materialFor(module.id()), "<white><bold>" + escape(module.displayName()) + "</bold>",
                    "<gray>Version:</gray> <white>" + escape(module.version()) + "</white>", "<gray>Mode:</gray> <aqua>Core API</aqua>",
                    "<gray>State:</gray> " + stateColor(module.state()) + module.state()), click -> {});
        }
        for (var legacy : modules.legacyModules()) {
            if (slot >= 44) break;
            builder.button(slot++, icon(materialFor(legacy.pluginName()), "<white><bold>" + escape(legacy.pluginName()) + "</bold>",
                    "<gray>Version:</gray> <white>" + escape(legacy.version()) + "</white>", "<gray>Mode:</gray> <yellow>Legacy / Standalone</yellow>",
                    "<gray>Enabled:</gray> " + (legacy.enabled() ? "<green>yes" : "<red>no")), click -> {});
        }
        builder.button(49, icon(Material.COMPARATOR, "<aqua><bold>Diagnostics</bold>",
                "<gray>Modules:</gray> <white>" + modules.totalDetected() + "</white>",
                "<gray>Integrations:</gray> <white>" + integrations.all().size() + "</white>",
                "<gray>Compute queue:</gray> <white>" + scheduler.computeQueueSize() + "</white>",
                "<gray>IO queue:</gray> <white>" + scheduler.ioQueueSize() + "</white>"), click -> {
            // GUI visibility is not authorization. Recheck at the authoritative callback boundary.
            withPermission(click.player(), "plexoncore.admin.diagnostics", () -> sendDiagnostics(click.player()));
        });
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
        if (configs.restartRequired()) send(sender, "<yellow>Restart required for:</yellow> <gray>" + escape(String.join(", ", configs.restartRequiredSettings())) + "</gray>");
    }

    private void sendModules(CommandSender sender) {
        if (modules.totalDetected() == 0) { send(sender, "<gray>No Plexon modules detected.</gray>"); return; }
        modules.registeredModules().forEach(module -> send(sender, "<aqua>" + escape(module.displayName()) + "</aqua> <dark_gray>—</dark_gray> " + stateColor(module.state()) + module.state() + " <dark_gray>| Core API | " + escape(module.version()) + "</dark_gray>"));
        modules.legacyModules().forEach(module -> send(sender, "<yellow>" + escape(module.pluginName()) + "</yellow> <dark_gray>— Legacy/Standalone | " + escape(module.version()) + "</dark_gray>"));
    }

    private void sendIntegrations(CommandSender sender) {
        integrations.all().forEach(integration -> {
            var status = integrations.status(integration.id()).orElse(null);
            String dimensions = status == null ? "" : " <dark_gray>| presence=" + status.presence() + ", provider=" + status.providerAvailability() + ", compatibility=" + status.compatibility() + "</dark_gray>";
            send(sender, "<white>" + integration.id() + "</white> <dark_gray>—</dark_gray> " + integrationColor(integration.state()) + integration.state()
                    + " <dark_gray>| " + escape(integration.provider()) + " " + escape(integration.version()) + "</dark_gray>" + dimensions);
        });
    }

    private void sendDiagnostics(CommandSender sender) {
        DetailedDiagnostics detailed = detailedSnapshot();
        DiagnosticsSnapshot snap = detailed.snapshot();
        send(sender, "<gray>Plugin/API:</gray> <white>" + snap.pluginVersion() + " / " + snap.apiVersion() + "</white> <dark_gray>(compat " + modules.compatibilityVersions().stream().map(CoreVersion::apiVersion).toList() + ")</dark_gray>");
        send(sender, "<gray>Paper:</gray> <white>" + escape(snap.paperVersion()) + "</white>");
        send(sender, "<gray>Java:</gray> <white>" + escape(snap.javaVersion()) + "</white>");
        send(sender, "<gray>Compute:</gray> <white>" + scheduler.activeWorkers() + "/" + scheduler.workerCount() + " active, queue " + scheduler.computeQueueSize() + ", rejected " + scheduler.rejectedComputeTasks() + "</white>");
        send(sender, "<gray>IO:</gray> <white>" + scheduler.activeIoWorkers() + "/" + scheduler.ioWorkerCount() + " active, queue " + scheduler.ioQueueSize() + ", rejected " + scheduler.rejectedIoTasks() + "</white>");
        send(sender, "<gray>Scheduler:</gray> <white>" + scheduler.failedTasks() + " failures, " + scheduler.ownerTaskCount() + " owner-scoped active tasks</white>");
        if (events != null) {
            var runtime = events.metrics();
            send(sender, "<gray>Block events:</gray> <white>" + runtime.blockBreakReceived() + " received / " + runtime.blockBreakRouted() + " routed, " + events.compiledBlockRoutes() + " material routes</white>");
            send(sender, "<gray>Context work:</gray> <white>" + runtime.contextsCreated() + " contexts, " + runtime.itemIdentityInspections() + " item inspections, " + runtime.pdcReads() + " PDC reads, " + runtime.originLookups() + " origin lookups</white>");
        }
        if (origins != null) {
            var origin = origins.stats();
            var persistence = origins.persistenceStats();
            send(sender, "<gray>Origin:</gray> <white>" + origin.knownChunks() + " known / " + origin.unknownChunks() + " unknown chunks, " + origin.trackedPlacedBlocks() + " placed positions</white>");
            send(sender, "<gray>Origin writer:</gray> <white>" + persistence.writerState() + ", " + persistence.pendingKeys() + " pending, batch " + persistence.activeBatchSize() + ", retries " + persistence.totalRetries() + ", connections " + persistence.writerConnectionOpens() + "</white>");
            if (persistence.lastFailure() != null) send(sender, "<yellow>Origin persistence:</yellow> <gray>" + escape(persistence.lastFailure()) + "</gray>");
        }
        send(sender, "<gray>GUI sessions:</gray> <white>" + snap.activeGuiSessions() + "</white>");
        send(sender, "<gray>Config reloads:</gray> <white>" + snap.successfulReloads() + " ok, " + snap.failedReloads() + " failed</white>");
        for (Contributor contributor : detailed.contributors()) {
            if (contributor.state() == ContributorState.READY) continue;
            send(sender, "<yellow>Health " + escape(contributor.id()) + ":</yellow> <white>" + contributor.state() + "</white> <gray>" + escape(contributor.detail()) + "</gray>");
        }
        if (!snap.startupWarnings().isEmpty()) snap.startupWarnings().forEach(w -> send(sender, "<yellow>Warning:</yellow> <gray>" + escape(w) + "</gray>"));
    }

    private void sendPerf(CommandSender sender) {
        if (events == null) { send(sender, "<gray>Core event runtime is not available.</gray>"); return; }
        var runtime = events.metrics();
        send(sender, "<gray>Gateway P50/P95/P99:</gray> <white>" + micros(runtime.gateway().p50Nanos()) + " / " + micros(runtime.gateway().p95Nanos()) + " / " + micros(runtime.gateway().p99Nanos()) + " µs</white>");
        send(sender, "<gray>Context P50/P95/P99:</gray> <white>" + micros(runtime.contextBuild().p50Nanos()) + " / " + micros(runtime.contextBuild().p95Nanos()) + " / " + micros(runtime.contextBuild().p99Nanos()) + " µs</white>");
        send(sender, "<gray>Dispatch P50/P95/P99:</gray> <white>" + micros(runtime.dispatch().p50Nanos()) + " / " + micros(runtime.dispatch().p95Nanos()) + " / " + micros(runtime.dispatch().p99Nanos()) + " µs</white>");
        send(sender, "<gray>Subscriber failures:</gray> <white>" + runtime.moduleFailures() + "</white>");
    }

    private void sendVersion(CommandSender sender) {
        send(sender, "<gray>PlexonCore:</gray> <white>" + version.pluginVersion() + "</white> <dark_gray>|</dark_gray> <gray>API:</gray> <white>" + version.apiVersion() + "</white>");
    }

    private void reload(CommandSender sender) {
        scheduler.supplyIo(configs::reloadCore).whenComplete((result, error) -> scheduler.runPrimary(() -> {
            if (error != null) { send(sender, "<red>Reload failed:</red> <gray>" + escape(error.getMessage()) + "</gray>"); return; }
            if (!result.valid()) { send(sender, "<red>Reload rejected:</red> <white>" + escape(result.path()) + "</white> <gray>" + escape(result.reason()) + "</gray>"); return; }
            modules.discoverLegacy(Bukkit.getPluginManager());
            integrations.refresh();
            if (configs.restartRequired()) {
                send(sender, "<yellow>Configuration validated and published, but restart is required for:</yellow> <gray>" + escape(String.join(", ", configs.restartRequiredSettings())) + "</gray>");
            } else {
                send(sender, "<green>PlexonCore configuration reloaded atomically.</green>");
            }
        }));
    }

    private boolean withPermission(CommandSender sender, String permission, Runnable action) {
        if (!sender.hasPermission(permission)) {
            send(sender, "<red>You do not have permission for that Core diagnostic.</red>");
            return true;
        }
        action.run();
        return true;
    }

    private void send(CommandSender sender, String message) {
        sender.sendMessage(text.render(TextService.TextMode.MINIMESSAGE, PREFIX + message));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length != 1) return List.of();
        String prefix = args[0].toLowerCase(Locale.ROOT);
        return List.of("status", "modules", "integrations", "diagnostics", "perf", "version", "reload").stream()
                .filter(s -> s.startsWith(prefix)).toList();
    }

    private static long micros(long nanos) { return nanos / 1_000L; }
    private static Material materialFor(String id) {
        String value = id.toLowerCase(Locale.ROOT);
        if (value.contains("quest")) return Material.BOOK;
        if (value.contains("rank")) return Material.NAME_TAG;
        if (value.contains("key")) return Material.TRIPWIRE_HOOK;
        if (value.contains("tool")) return Material.DIAMOND_PICKAXE;
        if (value.contains("blacksmith")) return Material.ANVIL;
        if (value.contains("shop")) return Material.EMERALD;
        if (value.contains("spawner")) return Material.SPAWNER;
        if (value.contains("chat")) return Material.PAPER;
        if (value.contains("backpack")) return Material.ENDER_CHEST;
        return Material.ENDER_EYE;
    }
    private static String stateColor(ModuleState state) { return switch (state) { case READY -> "<green>"; case DEGRADED, STARTING, DISCOVERED -> "<yellow>"; default -> "<red>"; }; }
    private static String integrationColor(IntegrationState state) { return state == IntegrationState.READY ? "<green>" : state == IntegrationState.MISSING ? "<gray>" : "<yellow>"; }
    private static String healthColor(HealthState state) { return state == HealthState.HEALTHY ? "<green>" : state == HealthState.DEGRADED ? "<yellow>" : "<red>"; }
    private static String escape(String value) { return value == null ? "" : value.replace("<", "\\<").replace(">", "\\>"); }

    public enum HealthState { HEALTHY, DEGRADED, FAILED, UNKNOWN }
    public record DiagnosticsSnapshot(String pluginVersion, String apiVersion, String paperVersion, String javaVersion, HealthState health,
                                      int registeredModules, int legacyModules, int integrationCount, int activeGuiSessions,
                                      int workerThreads, int activeWorkers, int executorQueue, boolean configValid,
                                      long successfulReloads, long failedReloads, List<String> startupWarnings, Instant capturedAt) {}
    public record DetailedDiagnostics(DiagnosticsSnapshot snapshot, List<Contributor> contributors) {
        public DetailedDiagnostics {
            Objects.requireNonNull(snapshot);
            contributors = contributors == null ? List.of() : List.copyOf(contributors);
        }
    }
}
