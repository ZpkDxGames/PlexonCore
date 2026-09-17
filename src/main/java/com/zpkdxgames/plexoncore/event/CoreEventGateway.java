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

import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BiConsumer;
import java.util.logging.Level;

public final class CoreEventGateway implements Listener, AutoCloseable {
    private static final long FAILURE_LOG_INTERVAL_NANOS = 10_000_000_000L;
    private final Plugin plugin;
    private final BlockOriginService origins;
    private final SubscriptionRegistry subscriptions = new SubscriptionRegistry();
    private final CoreRuntimeMetrics metrics = new CoreRuntimeMetrics();
    private final CoreItemIdentityResolver itemIdentityResolver = new CoreItemIdentityResolver(metrics);
    private final Map<String, Long> lastFailureLog = new ConcurrentHashMap<>();
    private final Map<BlockBreakEvent, PendingBlockBreak> pendingBlockBreaks = new IdentityHashMap<>();
    private final AtomicLong eventSequence = new AtomicLong();
    private final AtomicLong lastCallbackFailureNanos = new AtomicLong();

    public CoreEventGateway(Plugin plugin, BlockOriginService origins) {
        this.plugin = Objects.requireNonNull(plugin);
        this.origins = Objects.requireNonNull(origins);
    }

    /**
     * Legacy API 2.0 registration. Registrations created without an owner cannot be automatically
     * purged on another plugin's disable event, so new integrations should use the owner-aware overload.
     */
    public AutoCloseable subscribeBlockBreak(String moduleId, CoreBlockSubscription subscription, BlockBreakHandler handler) {
        Objects.requireNonNull(handler, "handler");
        return subscriptions.subscribe(moduleId, subscription, handler::handle);
    }

    /** API 2.1 owner-aware registration. */
    public AutoCloseable subscribeBlockBreak(Plugin owner, String moduleId, CoreBlockSubscription subscription, BlockBreakHandler handler) {
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(handler, "handler");
        if (!owner.isEnabled()) throw new IllegalStateException("Cannot register Core event subscription for disabled plugin " + owner.getName());
        return subscriptions.subscribe(owner, moduleId, subscription, handler::handle);
    }

    /** Removes only subscriptions owned by the exact plugin instance. */
    public int purgeOwner(Plugin owner) {
        return subscriptions.purgeOwner(owner);
    }

    public CoreRuntimeMetrics.Snapshot metrics() { return metrics.snapshot(); }
    public int compiledBlockRoutes() { return subscriptions.routeCount(); }
    public long lastCallbackFailureNanos() { return lastCallbackFailureNanos.get(); }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void captureBlockBreak(BlockBreakEvent event) {
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
            material, mainHand, origin, Bukkit.getCurrentTick(), System.nanoTime(), event.isDropItems()
        );
        metrics.contextCreated();
        metrics.contextNanos(System.nanoTime() - contextStart);
        pendingBlockBreaks.put(event, new PendingBlockBreak(plan, context, gatewayStart));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void dispatchFinalBlockBreak(BlockBreakEvent event) {
        PendingBlockBreak pending = pendingBlockBreaks.remove(event);
        if (pending == null) return;
        if (event.isCancelled()) {
            metrics.gatewayNanos(System.nanoTime() - pending.gatewayStart());
            return;
        }

        CoreBlockBreakContext finalContext = pending.context().withDropItems(event.isDropItems());
        long dispatchStart = System.nanoTime();
        dispatchCommitted(pending.plan(), finalContext, false, (moduleId, failure) -> {
            metrics.moduleFailure();
            lastCallbackFailureNanos.set(System.nanoTime());
            rateLimitedFailure(moduleId, failure);
        });
        metrics.dispatchNanos(System.nanoTime() - dispatchStart);
        metrics.gatewayNanos(System.nanoTime() - pending.gatewayStart());
    }

    /**
     * Package-private pure dispatch seam used by contract tests. A committed context is delivered
     * at most once to each enabled subscriber in the already-compiled route plan. Cancellation
     * short-circuits all subscribers; a failure in one module cannot block another subscriber.
     */
    static int dispatchCommitted(
            SubscriptionRegistry.RoutePlan plan,
            CoreBlockBreakContext context,
            boolean cancelled,
            BiConsumer<String, Throwable> failureHandler) {
        Objects.requireNonNull(plan, "plan");
        Objects.requireNonNull(context, "context");
        Objects.requireNonNull(failureHandler, "failureHandler");
        if (cancelled) return 0;

        int delivered = 0;
        for (SubscriptionRegistry.Subscriber subscriber : plan.subscribers()) {
            if (!subscriber.ownerEnabled()) continue;
            try {
                subscriber.handler().handle(context);
                delivered++;
            } catch (Throwable failure) {
                failureHandler.accept(subscriber.moduleId(), failure);
            }
        }
        return delivered;
    }

    private void rateLimitedFailure(String moduleId, Throwable failure) {
        long now = System.nanoTime();
        if (shouldEmitFailure(lastFailureLog, moduleId, now, FAILURE_LOG_INTERVAL_NANOS)) {
            plugin.getLogger().log(Level.SEVERE, "Core event subscriber failed: " + moduleId, failure);
        }
    }

    /** Timestamp advances only when the caller wins an actual emission slot. */
    static boolean shouldEmitFailure(Map<String, Long> timestamps, String key, long now, long intervalNanos) {
        Objects.requireNonNull(timestamps, "timestamps");
        Objects.requireNonNull(key, "key");
        AtomicBoolean emit = new AtomicBoolean();
        timestamps.compute(key, (ignored, previous) -> {
            if (previous == null || now - previous >= intervalNanos) {
                emit.set(true);
                return now;
            }
            return previous;
        });
        return emit.get();
    }

    @Override
    public void close() {
        pendingBlockBreaks.clear();
        subscriptions.clear();
        lastFailureLog.clear();
    }

    private record PendingBlockBreak(SubscriptionRegistry.RoutePlan plan, CoreBlockBreakContext context, long gatewayStart) {}

    @FunctionalInterface public interface BlockBreakHandler { void handle(CoreBlockBreakContext context); }
}
