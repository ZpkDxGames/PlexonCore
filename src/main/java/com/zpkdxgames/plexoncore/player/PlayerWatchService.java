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

/**
 * Shared, opt-in player activity gateway for Plexon modules.
 *
 * <p>The service owns the single Bukkit listener stack. Consumers register watches only while they
 * have pending work (for example a teleport warmup). Event callbacks are invoked synchronously in
 * the same main-thread event context as the underlying Bukkit event. The hot path performs one UUID
 * map lookup and returns immediately when a player has no watches.</p>
 *
 * <p>Subscriber failures are isolated. One module throwing from a watch callback cannot prevent
 * other watches from receiving the same signal or prevent terminal watch cleanup.</p>
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

    private record Registration(long id, EnumSet<WatchType> types, WatchListener listener) {}

    private final ConcurrentHashMap<UUID, ConcurrentHashMap<Long, Registration>> watches = new ConcurrentHashMap<>();
    private final AtomicLong sequence = new AtomicLong();
    private final AtomicBoolean closed = new AtomicBoolean();
    private final BiConsumer<Long, Throwable> failureHandler;

    public PlayerWatchService() {
        this((watchId, failure) -> {});
    }

    public PlayerWatchService(BiConsumer<Long, Throwable> failureHandler) {
        this.failureHandler = Objects.requireNonNull(failureHandler, "failureHandler");
    }

    public WatchHandle watch(UUID playerId, Set<WatchType> types, WatchListener listener) {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(types, "types");
        Objects.requireNonNull(listener, "listener");
        if (closed.get()) throw new IllegalStateException("PlayerWatchService is closed");
        if (types.isEmpty()) throw new IllegalArgumentException("At least one watch type is required");

        EnumSet<WatchType> copy = EnumSet.copyOf(types);
        long id = sequence.incrementAndGet();
        watches.computeIfAbsent(playerId, ignored -> new ConcurrentHashMap<>())
                .put(id, new Registration(id, copy, listener));
        return new Handle(playerId, id);
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
        var registrations = watches.get(player.getUniqueId());
        if (registrations == null || registrations.isEmpty() || !contains(registrations, WatchType.MOVEMENT)) return;

        Location from = event.getFrom();
        Location to = event.getTo();
        if (to == null || samePosition(from, to)) return;
        dispatch(registrations, WatchType.MOVEMENT, new PlayerActivity(
                player.getUniqueId(), Signal.MOVED, Position.from(from), Position.from(to), 0.0D, System.nanoTime()));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        var registrations = watches.get(player.getUniqueId());
        if (registrations == null || registrations.isEmpty() || !contains(registrations, WatchType.DAMAGE)) return;
        dispatch(registrations, WatchType.DAMAGE, new PlayerActivity(
                player.getUniqueId(), Signal.DAMAGED, Position.from(player.getLocation()), null,
                event.getFinalDamage(), System.nanoTime()));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        dispatchAndClear(event.getPlayer(), WatchType.QUIT, Signal.QUIT);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onWorldChange(PlayerChangedWorldEvent event) {
        Player player = event.getPlayer();
        var registrations = watches.get(player.getUniqueId());
        if (registrations == null || registrations.isEmpty() || !contains(registrations, WatchType.WORLD_CHANGE)) return;
        Position from = new Position(event.getFrom().getUID(), player.getX(), player.getY(), player.getZ());
        dispatch(registrations, WatchType.WORLD_CHANGE, new PlayerActivity(
                player.getUniqueId(), Signal.WORLD_CHANGED, from, Position.from(player.getLocation()), 0.0D, System.nanoTime()));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(PlayerDeathEvent event) {
        dispatchAndClear(event.getEntity(), WatchType.DEATH, Signal.DIED);
    }

    private void dispatchAndClear(Player player, WatchType type, Signal signal) {
        var registrations = watches.get(player.getUniqueId());
        if (registrations == null || registrations.isEmpty()) return;
        try {
            if (contains(registrations, type)) {
                dispatch(registrations, type, new PlayerActivity(
                        player.getUniqueId(), signal, Position.from(player.getLocation()), null, 0.0D, System.nanoTime()));
            }
        } finally {
            watches.remove(player.getUniqueId(), registrations);
        }
    }

    private static boolean contains(ConcurrentHashMap<Long, Registration> registrations, WatchType type) {
        for (Registration registration : registrations.values()) {
            if (registration.types().contains(type)) return true;
        }
        return false;
    }

    private int dispatch(
            ConcurrentHashMap<Long, Registration> registrations,
            WatchType type,
            PlayerActivity activity) {
        int delivered = 0;
        for (Registration registration : registrations.values()) {
            if (!registration.types().contains(type)) continue;
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

    @Override
    public void close() {
        if (closed.compareAndSet(false, true)) watches.clear();
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
                return registrations.isEmpty() ? null : registrations;
            });
        }
    }
}
