package com.zpkdxgames.plexoncore.event;

import com.zpkdxgames.plexoncore.context.BlockOrigin;
import com.zpkdxgames.plexoncore.context.CoreBlockBreakContext;
import com.zpkdxgames.plexoncore.context.CoreItemIdentity;
import com.zpkdxgames.plexoncore.origin.BlockOriginService;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.plugin.Plugin;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;

public final class CoreEventGateway implements Listener {
    private static final long FAILURE_LOG_INTERVAL_NANOS = 10_000_000_000L;
    private final Plugin plugin;
    private final BlockOriginService origins;
    private final SubscriptionRegistry subscriptions = new SubscriptionRegistry();
    private final CoreRuntimeMetrics metrics = new CoreRuntimeMetrics();
    private final CoreItemIdentityResolver itemIdentityResolver = new CoreItemIdentityResolver(metrics);
    private final Map<String, Long> lastFailureLog = new ConcurrentHashMap<>();
    private final AtomicLong eventSequence = new AtomicLong();

    public CoreEventGateway(Plugin plugin, BlockOriginService origins) {
        this.plugin = Objects.requireNonNull(plugin);
        this.origins = Objects.requireNonNull(origins);
    }

    public AutoCloseable subscribeBlockBreak(String moduleId, CoreBlockSubscription subscription, BlockBreakHandler handler) {
        Objects.requireNonNull(handler, "handler");
        return subscriptions.subscribe(moduleId, subscription, handler::handle);
    }

    public CoreRuntimeMetrics.Snapshot metrics() { return metrics.snapshot(); }
    public int compiledBlockRoutes() { return subscriptions.routeCount(); }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        long gatewayStart = System.nanoTime();
        metrics.blockBreakReceived();
        Block block = event.getBlock();
        Material material = block.getType();
        SubscriptionRegistry.RoutePlan plan = subscriptions.plan(material);
        if (plan.empty()) {
            metrics.gatewayNanos(System.nanoTime() - gatewayStart);
            return;
        }
        metrics.blockBreakRouted();

        long contextStart = System.nanoTime();
        int x = block.getX(); int y = block.getY(); int z = block.getZ();
        World world = block.getWorld();
        UUID worldId = world.getUID();
        BlockOrigin origin = BlockOrigin.UNKNOWN;
        if (plan.requiresNaturalOrigin()) {
            metrics.originLookup();
            origin = origins.origin(worldId, x, y, z);
        }
        Player player = event.getPlayer();
        CoreItemIdentity mainHand = plan.itemIdentityNamespaces().isEmpty()
            ? null
            : itemIdentityResolver.resolve(player.getInventory().getItemInMainHand(), plan.itemIdentityNamespaces());
        CoreBlockBreakContext context = new CoreBlockBreakContext(
            eventSequence.incrementAndGet(), player.getUniqueId(), player.getName(), worldId, world.getName(), x, y, z,
            material, mainHand, origin, Bukkit.getCurrentTick(), System.nanoTime()
        );
        metrics.contextCreated();
        metrics.contextNanos(System.nanoTime() - contextStart);

        long dispatchStart = System.nanoTime();
        for (SubscriptionRegistry.Subscriber subscriber : plan.subscribers()) {
            try { subscriber.handler().handle(context); }
            catch (Throwable failure) { metrics.moduleFailure(); rateLimitedFailure(subscriber.moduleId(), failure); }
        }
        metrics.dispatchNanos(System.nanoTime() - dispatchStart);
        metrics.gatewayNanos(System.nanoTime() - gatewayStart);
    }

    private void rateLimitedFailure(String moduleId, Throwable failure) {
        long now = System.nanoTime(); Long previous = lastFailureLog.put(moduleId, now);
        if (previous == null || now - previous >= FAILURE_LOG_INTERVAL_NANOS) plugin.getLogger().log(Level.SEVERE, "Core event subscriber failed: " + moduleId, failure);
    }

    @FunctionalInterface public interface BlockBreakHandler { void handle(CoreBlockBreakContext context); }
}
