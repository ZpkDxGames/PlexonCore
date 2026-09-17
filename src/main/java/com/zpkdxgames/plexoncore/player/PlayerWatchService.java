package com.zpkdxgames.plexoncore.player;

import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BiConsumer;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;

/**
 * Shared, opt-in player activity gateway for Plexon modules.
 *
 * <p>The service owns the single Bukkit listener stack. Consumers register watches only while they
 * have pending work (for example a teleport warmup). Event callbacks are invoked synchronously in
 * the same main-thread event context as the underlying Bukkit event. The hot path performs one UUID
 * map lookup and returns immediately when a player has no watches.</p>
 *
 * <p>API 2.1 adds owner-aware registrations. Legacy ownerless registrations are retained for binary
 * and source compatibility but cannot be automatically attributed to a disabling plugin.</p>
 */
public final class PlayerWatchService implements Listener, AutoCloseable {
    public enum WatchType {
        MOVEMENT,
        DAMAGE,
        QUIT,
        WORLD_CHANGE,
        DEATH
    }

    public enum Signal {
        MOVED,
        DAMAGED,
        QUIT,
        WORLD_CHANGED,
        DIED
    }

    public record Position(UUID worldId, double x, double y, double z) {
        public Position {
            Objects.requireNonNull(worldId, "worldId");
        }

        public static Position from(Location location) {
            if (location == null || location.getWorld() == null) return null;
            return new Position(location.getWorld().getUID(), location.getX(), location.getY(), location.getZ());
        }
    }

    public record PlayerActivity(
            UUID playerId,
            Signal signal,
            Position from,
            Position to,
            double finalDamage,
            long observedAtNanos) {}

    /** Additive API 2.1 view for callers that want explicit transition completeness. */
    public record WorldTransition(Position before, Position after, boolean complete) {
        public static WorldTransition from(PlayerActivity activity) {
            Objects.requireNonNull(activity, "activity");
            return new WorldTransition(activity.from(), activity.to(), activity.from() != null && activity.to() != null
                    && Double.isFinite(activity.from().x()) && Double.isFinite(activity.from().y()) && Double.isFinite(activity.from().z()));
        }
    }

    @FunctionalInterface
    public interface WatchListener {
        void onActivity(PlayerActivity activity);
    }

    public interface WatchHandle extends AutoCloseable {
        UUID playerId();
        long id();
        boolean isClosed();
        @Override void close();
    }

    public record WatchStats(int watchedPlayers, int registrations) {}

    private record Registration(long id, Plugin owner, EnumSet<WatchType> types, WatchListener listener) {
        boolean ownerEnabled() { return owner == null || owner.isEnabled(); }
    }

    private final ConcurrentHashMap<UUID, ConcurrentHashMap<Long, Registration>> watches = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, Position> lastPositions = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, WorldTransition> pendingWorldTransitions = new ConcurrentHashMap<>();
    private final AtomicLong sequence = new AtomicLong();
    private final AtomicBoolean closed = new AtomicBoolean();
    private final BiConsumer<Long, Throwable> failureHandler;

    public PlayerWatchService() {
        this((watchId, failure) -> {});
    }

    public PlayerWatchService(BiConsumer<Long, Throwable> failureHandler) {
        this.failureHandler = Objects.requireNonNull(failureHandler, "failureHandler");
    }

    /** Legacy API 2.0 registration. Prefer the owner-aware overload for new code. */
    public WatchHandle watch(UUID playerId, Set<WatchType> types, WatchListener listener) {
        return watchInternal(null, playerId, types, listener);
    }

    /** API 2.1 owner-aware registration. */
    public WatchHandle watch(Plugin owner, UUID playerId, Set<WatchType> types, WatchListener listener) {
        Objects.requireNonNull(owner, "owner");
        if (!owner.isEnabled()) throw new IllegalStateException("Cannot register player watch for disabled plugin " + owner.getName());
        return watchInternal(owner, playerId, types, listener);
    }

    private WatchHandle watchInternal(Plugin owner, UUID playerId, Set<WatchType> types, WatchListener listener) {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(types, "types");
        Objects.requireNonNull(listener, "listener");
        if (closed.get()) throw new IllegalStateException("PlayerWatchService is closed");
        if (types.isEmpty()) throw new IllegalArgumentException("At least one watch type is required");

        EnumSet<WatchType> copy = EnumSet.copyOf(types);
        long id = sequence.incrementAndGet();
        watches.computeIfAbsent(playerId, ignored -> new ConcurrentHashMap<>())
                .put(id, new Registration(id, owner, copy, listener));
        return new Handle(playerId, id);
    }

    /** Removes only registrations owned by the exact plugin instance. */
    public int purgeOwner(Plugin owner) {
        if (owner == null) return 0;
        AtomicLong removed = new AtomicLong();
        watches.forEach((playerId, registrations) -> {
            registrations.forEach((id, registration) -> {
                if (registration.owner() == owner && registrations.remove(id, registration)) removed.incrementAndGet();
            });
            if (registrations.isEmpty() && watches.remove(playerId, registrations)) clearPositionState(playerId);
        });
        return Math.toIntExact(removed.get());
    }

    public boolean hasWatches(UUID playerId) {
        var registrations = watches.get(playerId);
        return registrations != null && !registrations.isEmpty();
    }

    public WatchStats stats() {
        int registrations = 0;
        for (var value : watches.values()) registrations += value.size();
        return new WatchStats(watches.size(), registrations);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();
        var registrations = watches.get(playerId);
        if (registrations == null || registrations.isEmpty()) return;

        Location from = event.getFrom();
        Location to = event.getTo();
        if (to == null || samePosition(from, to)) return;
        Position before = Position.from(from);
        Position after = Position.from(to);
        observeMovement(playerId, before, after);

        if (contains(registrations, WatchType.MOVEMENT)) {
            dispatch(registrations, WatchType.MOVEMENT, new PlayerActivity(
                    playerId, Signal.MOVED, before, after, 0.0D, System.nanoTime()));
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        var registrations = watches.get(player.getUniqueId());
        if (registrations == null || registrations.isEmpty() || !contains(registrations, WatchType.DAMAGE)) return;
        Position current = Position.from(player.getLocation());
        if (current != null) lastPositions.put(player.getUniqueId(), current);
        dispatch(registrations, WatchType.DAMAGE, new PlayerActivity(
                player.getUniqueId(), Signal.DAMAGED, current, null,
                event.getFinalDamage(), System.nanoTime()));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        dispatchAndClear(event.getPlayer(), WatchType.QUIT, Signal.QUIT);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onWorldChange(PlayerChangedWorldEvent event) {
        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();
        var registrations = watches.get(playerId);
        if (registrations == null || registrations.isEmpty() || !contains(registrations, WatchType.WORLD_CHANGE)) return;
        Position current = Position.from(player.getLocation());
        PlayerActivity activity = worldChangeActivity(playerId, event.getFrom().getUID(), current);
        dispatch(registrations, WatchType.WORLD_CHANGE, activity);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(PlayerDeathEvent event) {
        dispatchAndClear(event.getEntity(), WatchType.DEATH, Signal.DIED);
    }

    void observeMovement(UUID playerId, Position before, Position after) {
        if (before != null && after != null && !before.worldId().equals(after.worldId())) {
            pendingWorldTransitions.put(playerId, new WorldTransition(before, after, true));
        }
        if (after != null) lastPositions.put(playerId, after);
    }

    PlayerActivity worldChangeActivity(UUID playerId, UUID oldWorldId, Position current) {
        WorldTransition observed = pendingWorldTransitions.remove(playerId);
        Position before = null;
        Position after = current;
        if (observed != null && observed.before() != null && observed.before().worldId().equals(oldWorldId)) {
            before = observed.before();
            if (after == null) after = observed.after();
        } else {
            Position last = lastPositions.get(playerId);
            if (last != null && last.worldId().equals(oldWorldId)) before = last;
        }
        if (before == null) {
            // PlayerChangedWorldEvent exposes only the previous World, not its coordinates. Unknown
            // coordinates are represented explicitly instead of combining an old world UUID with
            // destination coordinates as older Core versions did.
            before = new Position(oldWorldId, Double.NaN, Double.NaN, Double.NaN);
        }
        if (after != null) lastPositions.put(playerId, after);
        return new PlayerActivity(playerId, Signal.WORLD_CHANGED, before, after, 0.0D, System.nanoTime());
    }

    private void dispatchAndClear(Player player, WatchType type, Signal signal) {
        UUID playerId = player.getUniqueId();
        var registrations = watches.get(playerId);
        if (registrations == null || registrations.isEmpty()) return;
        try {
            if (contains(registrations, type)) {
                dispatch(registrations, type, new PlayerActivity(
                        playerId, signal, Position.from(player.getLocation()), null, 0.0D, System.nanoTime()));
            }
        } finally {
            watches.remove(playerId, registrations);
            clearPositionState(playerId);
        }
    }

    private static boolean contains(ConcurrentHashMap<Long, Registration> registrations, WatchType type) {
        for (Registration registration : registrations.values()) {
            if (registration.ownerEnabled() && registration.types().contains(type)) return true;
        }
        return false;
    }

    private int dispatch(
            ConcurrentHashMap<Long, Registration> registrations,
            WatchType type,
            PlayerActivity activity) {
        int delivered = 0;
        for (Registration registration : registrations.values()) {
            if (!registration.ownerEnabled() || !registration.types().contains(type)) continue;
            try {
                registration.listener().onActivity(activity);
                delivered++;
            } catch (Throwable failure) {
                reportFailure(registration.id(), failure);
            }
        }
        return delivered;
    }

    int dispatchWatchedActivity(UUID playerId, WatchType type, PlayerActivity activity) {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(activity, "activity");
        var registrations = watches.get(playerId);
        if (registrations == null || registrations.isEmpty()) return 0;
        return dispatch(registrations, type, activity);
    }

    private void reportFailure(long watchId, Throwable failure) {
        try {
            failureHandler.accept(watchId, failure);
        } catch (Throwable ignored) {
            // Failure reporting must never break the shared event gateway.
        }
    }

    static boolean samePosition(Location from, Location to) {
        if (from == null || to == null || from.getWorld() != to.getWorld()) return false;
        return Double.compare(from.getX(), to.getX()) == 0
                && Double.compare(from.getY(), to.getY()) == 0
                && Double.compare(from.getZ(), to.getZ()) == 0;
    }

    private void clearPositionState(UUID playerId) {
        lastPositions.remove(playerId);
        pendingWorldTransitions.remove(playerId);
    }

    @Override
    public void close() {
        if (closed.compareAndSet(false, true)) {
            watches.clear();
            lastPositions.clear();
            pendingWorldTransitions.clear();
        }
    }

    private final class Handle implements WatchHandle {
        private final UUID playerId;
        private final long id;
        private final AtomicBoolean handleClosed = new AtomicBoolean();

        private Handle(UUID playerId, long id) {
            this.playerId = playerId;
            this.id = id;
        }

        @Override public UUID playerId() { return playerId; }
        @Override public long id() { return id; }
        @Override public boolean isClosed() { return handleClosed.get(); }

        @Override
        public void close() {
            if (!handleClosed.compareAndSet(false, true)) return;
            watches.computeIfPresent(playerId, (ignored, registrations) -> {
                registrations.remove(id);
                if (registrations.isEmpty()) {
                    clearPositionState(playerId);
                    return null;
                }
                return registrations;
            });
        }
    }
}
