package com.zpkdxgames.plexoncore.origin;

import com.zpkdxgames.plexoncore.context.BlockOrigin;
import com.zpkdxgames.plexoncore.persistence.SqliteService;
import com.zpkdxgames.plexoncore.persistence.SqliteService.Migration;
import com.zpkdxgames.plexoncore.persistence.SqliteService.SqliteDatabase;
import com.zpkdxgames.plexoncore.scheduler.CoreScheduler;
import java.nio.file.Path;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockFadeEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.ChunkUnloadEvent;
import org.bukkit.plugin.Plugin;

public final class BlockOriginService implements Listener, AutoCloseable {
    private static final Duration DEFAULT_FLUSH_INTERVAL = Duration.ofMillis(500);
    private static final int DEFAULT_BATCH_SIZE = 512;
    private static final int DEFAULT_PRESSURE_THRESHOLD = 2048;
    private static final int DEFAULT_MAX_RETRIES = 6;

    private final Plugin plugin;
    private final CoreScheduler scheduler;
    private final Map<ChunkKey, ChunkState> chunks = new ConcurrentHashMap<>();
    private final CoalescingMutationBuffer<Position> mutations = new CoalescingMutationBuffer<>();
    private final AtomicBoolean persistenceReady = new AtomicBoolean();
    private final AtomicBoolean acceptingMutations = new AtomicBoolean(true);
    private final AtomicBoolean flushInProgress = new AtomicBoolean();
    private final AtomicInteger activeBatchSize = new AtomicInteger();
    private final AtomicInteger retryStreak = new AtomicInteger();
    private final AtomicLong totalRetries = new AtomicLong();
    private final AtomicReference<CompletableFuture<Void>> activeFlush = new AtomicReference<>(CompletableFuture.completedFuture(null));

    private volatile SqliteDatabase database;
    private volatile Throwable persistenceFailure;
    private volatile Instant lastSuccessfulWrite;
    private volatile Instant lastFailureAt;
    private volatile WriterState writerState = WriterState.STARTING;
    private volatile Duration flushInterval = DEFAULT_FLUSH_INTERVAL;
    private volatile int batchSize = DEFAULT_BATCH_SIZE;
    private volatile int pressureThreshold = DEFAULT_PRESSURE_THRESHOLD;
    private volatile int maxRetries = DEFAULT_MAX_RETRIES;
    private volatile CoreScheduler.TaskHandle periodicFlush;
    private volatile CoreScheduler.TaskHandle retryTask;

    public BlockOriginService(Plugin plugin, CoreScheduler scheduler) {
        this.plugin = Objects.requireNonNull(plugin);
        this.scheduler = Objects.requireNonNull(scheduler);
    }

    /** Configure bounded write behavior before start. */
    public synchronized void configurePersistence(Duration interval, int batchSize, int pressureThreshold, int maxRetries) {
        if (database != null) throw new IllegalStateException("BlockOrigin persistence is already started");
        Duration normalized = Objects.requireNonNull(interval, "interval");
        if (normalized.isNegative() || normalized.isZero() || normalized.compareTo(Duration.ofSeconds(30)) > 0) {
            throw new IllegalArgumentException("flush interval must be > 0 and <= 30 seconds");
        }
        if (batchSize < 16 || batchSize > 10_000) throw new IllegalArgumentException("batchSize must be 16..10000");
        if (pressureThreshold < batchSize || pressureThreshold > 100_000) throw new IllegalArgumentException("pressureThreshold must be >= batchSize and <= 100000");
        if (maxRetries < 0 || maxRetries > 20) throw new IllegalArgumentException("maxRetries must be 0..20");
        this.flushInterval = normalized;
        this.batchSize = batchSize;
        this.pressureThreshold = pressureThreshold;
        this.maxRetries = maxRetries;
    }

    public void start(SqliteService sqlite, Path databasePath) {
        try {
            database = sqlite.open(databasePath);
            database.migrate(List.of(
                    new Migration(200, "Core block origin index", connection -> {
                        try (var statement = connection.createStatement()) {
                            statement.execute("CREATE TABLE IF NOT EXISTS core_block_origin(world_id TEXT NOT NULL, chunk_x INTEGER NOT NULL, chunk_z INTEGER NOT NULL, packed_pos INTEGER NOT NULL, PRIMARY KEY(world_id, chunk_x, chunk_z, packed_pos))");
                            statement.execute("CREATE INDEX IF NOT EXISTS idx_core_block_origin_chunk ON core_block_origin(world_id, chunk_x, chunk_z)");
                        }
                    }),
                    new Migration(201, "Core block origin migration markers", connection -> {
                        try (var statement = connection.createStatement()) {
                            statement.execute("CREATE TABLE IF NOT EXISTS core_block_origin_import(world_id TEXT NOT NULL, chunk_x INTEGER NOT NULL, chunk_z INTEGER NOT NULL, source TEXT NOT NULL, source_version INTEGER NOT NULL, completed_at INTEGER NOT NULL, PRIMARY KEY(world_id, chunk_x, chunk_z, source, source_version))");
                        }
                    })))
                    .whenComplete((version, error) -> {
                        if (error != null) {
                            failPersistence(error, WriterState.FAILED);
                            safeSevere("Block origin persistence unavailable: " + persistenceFailure.getMessage());
                            return;
                        }
                        persistenceReady.set(true);
                        writerState = WriterState.READY;
                        periodicFlush = scheduler.scheduleRepeatingAsync(plugin, flushInterval, flushInterval, this::requestFlush);
                        requestFlush();
                        scheduler.runPrimary(this::loadCurrentlyLoadedChunks);
                    });
        } catch (Exception error) {
            failPersistence(error, WriterState.FAILED);
            safeSevere("Block origin database could not be opened: " + error.getMessage());
        }
    }

    public BlockOrigin origin(Block block) {
        return origin(block.getWorld().getUID(), block.getX(), block.getY(), block.getZ());
    }

    public BlockOrigin origin(UUID worldId, int x, int y, int z) {
        ChunkState state = chunks.get(key(worldId, x >> 4, z >> 4));
        if (state == null) return BlockOrigin.UNKNOWN;
        long packed = pack(x, y, z);
        if (state.playerPlaced.contains(packed)) return BlockOrigin.PLAYER_PLACED;
        return state.loadedFromPersistence ? BlockOrigin.NATURAL : BlockOrigin.UNKNOWN;
    }

    /**
     * Idempotently imports player-placed coordinates from another module. The returned future
     * completes after the batch is persisted. This low-level form does not write a migration marker.
     */
    public CompletableFuture<Integer> importPlayerPlaced(UUID worldId, Collection<BlockPosition> positions) {
        Objects.requireNonNull(worldId, "worldId");
        Objects.requireNonNull(positions, "positions");
        if (positions.isEmpty()) return CompletableFuture.completedFuture(0);
        SqliteDatabase db = readyDatabase();
        if (db == null) return CompletableFuture.failedFuture(new IllegalStateException("Core block-origin persistence is not ready"));

        List<Position> inserts = prepareImport(worldId, positions, null);
        if (inserts.isEmpty()) return CompletableFuture.completedFuture(0);
        return persistImport(db, inserts).thenApply(ignored -> inserts.size());
    }

    /**
     * Imports one legacy chunk and records a Core-owned completion marker in the same transaction.
     * Retrying the same source/version is safe. Coordinates outside the declared chunk are rejected.
     */
    public CompletableFuture<Integer> importPlayerPlacedChunk(
            UUID worldId,
            int chunkX,
            int chunkZ,
            String source,
            int sourceVersion,
            Collection<BlockPosition> positions) {
        Objects.requireNonNull(worldId, "worldId");
        Objects.requireNonNull(positions, "positions");
        String normalizedSource = normalizeSource(source);
        if (sourceVersion <= 0) throw new IllegalArgumentException("sourceVersion must be positive");
        SqliteDatabase db = readyDatabase();
        if (db == null) return CompletableFuture.failedFuture(new IllegalStateException("Core block-origin persistence is not ready"));

        ChunkKey required = key(worldId, chunkX, chunkZ);
        List<Position> inserts = prepareImport(worldId, positions, required);
        return db.executeWrite(connection -> {
            if (!inserts.isEmpty()) {
                try (PreparedStatement ps = connection.prepareStatement("INSERT OR IGNORE INTO core_block_origin(world_id,chunk_x,chunk_z,packed_pos) VALUES(?,?,?,?)")) {
                    for (Position position : inserts) {
                        bind(ps, position.key, position.packed);
                        ps.addBatch();
                    }
                    ps.executeBatch();
                }
            }
            try (PreparedStatement marker = connection.prepareStatement("INSERT OR REPLACE INTO core_block_origin_import(world_id,chunk_x,chunk_z,source,source_version,completed_at) VALUES(?,?,?,?,?,?)")) {
                marker.setString(1, worldId.toString());
                marker.setInt(2, chunkX);
                marker.setInt(3, chunkZ);
                marker.setString(4, normalizedSource);
                marker.setInt(5, sourceVersion);
                marker.setLong(6, System.currentTimeMillis());
                marker.executeUpdate();
            }
        }).thenApply(ignored -> inserts.size());
    }

    /** Returns whether Core persistence contains the matching completed import marker. */
    public CompletableFuture<Boolean> importComplete(
            UUID worldId, int chunkX, int chunkZ, String source, int sourceVersion) {
        Objects.requireNonNull(worldId, "worldId");
        String normalizedSource = normalizeSource(source);
        if (sourceVersion <= 0) throw new IllegalArgumentException("sourceVersion must be positive");
        SqliteDatabase db = readyDatabase();
        if (db == null) return CompletableFuture.failedFuture(new IllegalStateException("Core block-origin persistence is not ready"));
        return db.query(connection -> {
            try (PreparedStatement ps = connection.prepareStatement("SELECT 1 FROM core_block_origin_import WHERE world_id=? AND chunk_x=? AND chunk_z=? AND source=? AND source_version=? LIMIT 1")) {
                ps.setString(1, worldId.toString());
                ps.setInt(2, chunkX);
                ps.setInt(3, chunkZ);
                ps.setString(4, normalizedSource);
                ps.setInt(5, sourceVersion);
                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next();
                }
            } catch (Exception error) {
                throw new IllegalStateException("Could not read block-origin import marker", error);
            }
        });
    }

    private SqliteDatabase readyDatabase() {
        SqliteDatabase db = database;
        return db != null && persistenceReady.get() ? db : null;
    }

    private List<Position> prepareImport(UUID worldId, Collection<BlockPosition> positions, ChunkKey requiredChunk) {
        List<Position> inserts = new ArrayList<>(positions.size());
        for (BlockPosition coordinate : positions) {
            if (coordinate == null) continue;
            ChunkKey chunkKey = key(worldId, coordinate.x() >> 4, coordinate.z() >> 4);
            if (requiredChunk != null && !requiredChunk.equals(chunkKey)) {
                throw new IllegalArgumentException("Imported block coordinate is outside the declared chunk");
            }
            long packed = pack(coordinate.x(), coordinate.y(), coordinate.z());
            ChunkState state = chunks.computeIfAbsent(chunkKey, ignored -> new ChunkState());
            synchronized (state) {
                state.playerPlaced.add(packed);
                if (!state.loadedFromPersistence) {
                    state.addedBeforeLoad.add(packed);
                    state.removedBeforeLoad.remove(packed);
                }
            }
            inserts.add(new Position(chunkKey, packed));
        }
        return inserts;
    }

    private CompletableFuture<Void> persistImport(SqliteDatabase db, List<Position> inserts) {
        return db.executeWrite(connection -> {
            try (PreparedStatement ps = connection.prepareStatement("INSERT OR IGNORE INTO core_block_origin(world_id,chunk_x,chunk_z,packed_pos) VALUES(?,?,?,?)")) {
                for (Position position : inserts) {
                    bind(ps, position.key, position.packed);
                    ps.addBatch();
                }
                ps.executeBatch();
            }
        });
    }

    private static String normalizeSource(String source) {
        String normalized = Objects.requireNonNull(source, "source").trim().toLowerCase(Locale.ROOT);
        if (!normalized.matches("[a-z0-9._-]{1,64}")) throw new IllegalArgumentException("Invalid origin import source: " + source);
        return normalized;
    }

    public Stats stats() {
        long tracked = chunks.values().stream().mapToLong(state -> state.playerPlaced.size()).sum();
        long loaded = chunks.values().stream().filter(state -> state.loadedFromPersistence).count();
        int databaseQueue = database == null ? 0 : database.queuedWrites();
        return new Stats(chunks.size(), loaded, chunks.size() - loaded, tracked,
                Math.addExact(mutations.pendingSize(), databaseQueue), persistenceReady.get(),
                persistenceFailure == null ? null : persistenceFailure.getMessage());
    }

    public PersistenceStats persistenceStats() {
        SqliteDatabase db = database;
        return new PersistenceStats(mutations.pendingSize(), activeBatchSize.get(), totalRetries.get(), retryStreak.get(),
                lastSuccessfulWrite, lastFailureAt,
                persistenceFailure == null ? null : persistenceFailure.getMessage(), writerState,
                db == null ? 0 : db.writerConnectionOpenCount());
    }

    /** Explicitly retries retained failed work after an exhausted retry sequence. */
    public void retryPersistence() {
        retryStreak.set(0);
        if (persistenceReady.get()) {
            writerState = WriterState.DEGRADED;
            requestFlush();
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) { markPlaced(event.getBlockPlaced()); }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) { removeTracked(List.of(event.getBlock())); }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBurn(BlockBurnEvent event) { removeTracked(List.of(event.getBlock())); }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onFade(BlockFadeEvent event) { removeTracked(List.of(event.getBlock())); }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) { removeTracked(event.blockList()); }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) { removeTracked(event.blockList()); }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPistonExtend(BlockPistonExtendEvent event) { moveTracked(event.getBlocks(), event.getDirection()); }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPistonRetract(BlockPistonRetractEvent event) { moveTracked(event.getBlocks(), event.getDirection().getOppositeFace()); }

    @EventHandler
    public void onChunkLoad(ChunkLoadEvent event) { loadChunk(event.getChunk()); }

    @EventHandler
    public void onChunkUnload(ChunkUnloadEvent event) { chunks.remove(key(event.getWorld().getUID(), event.getChunk().getX(), event.getChunk().getZ())); }

    private void loadCurrentlyLoadedChunks() {
        for (World world : Bukkit.getWorlds()) for (Chunk chunk : world.getLoadedChunks()) loadChunk(chunk);
    }

    private void loadChunk(Chunk chunk) {
        ChunkKey key = key(chunk.getWorld().getUID(), chunk.getX(), chunk.getZ());
        ChunkState state = chunks.computeIfAbsent(key, ignored -> new ChunkState());
        if (!persistenceReady.get() || database == null || state.loading || state.loadedFromPersistence) return;
        state.loading = true;
        database.query(connection -> {
            Set<Long> positions = ConcurrentHashMap.newKeySet();
            try (PreparedStatement ps = connection.prepareStatement("SELECT packed_pos FROM core_block_origin WHERE world_id=? AND chunk_x=? AND chunk_z=?")) {
                ps.setString(1, key.worldId.toString());
                ps.setInt(2, key.chunkX);
                ps.setInt(3, key.chunkZ);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) positions.add(rs.getLong(1));
                }
                return positions;
            } catch (Exception error) {
                throw new IllegalStateException(error);
            }
        }).whenComplete((positions, error) -> {
            if (error != null) {
                state.loading = false;
                failPersistence(error, WriterState.DEGRADED);
                return;
            }
            synchronized (state) {
                state.playerPlaced.addAll(positions);
                state.playerPlaced.removeAll(state.removedBeforeLoad);
                state.playerPlaced.addAll(state.addedBeforeLoad);
                state.loadedFromPersistence = true;
                state.loading = false;
                state.removedBeforeLoad.clear();
                state.addedBeforeLoad.clear();
            }
        });
    }

    private void markPlaced(Block block) {
        ChunkKey key = key(block.getWorld().getUID(), block.getX() >> 4, block.getZ() >> 4);
        long packed = pack(block.getX(), block.getY(), block.getZ());
        ChunkState state = chunks.computeIfAbsent(key, ignored -> new ChunkState());
        synchronized (state) {
            state.playerPlaced.add(packed);
            if (!state.loadedFromPersistence) {
                state.addedBeforeLoad.add(packed);
                state.removedBeforeLoad.remove(packed);
            }
        }
        queueMutation(new Position(key, packed), CoalescingMutationBuffer.State.PRESENT);
    }

    private void removeTracked(Collection<Block> blocks) {
        if (blocks == null || blocks.isEmpty()) return;
        for (Block block : blocks) {
            ChunkKey key = key(block.getWorld().getUID(), block.getX() >> 4, block.getZ() >> 4);
            ChunkState state = chunks.computeIfAbsent(key, ignored -> new ChunkState());
            long packed = pack(block.getX(), block.getY(), block.getZ());
            boolean persistDelete;
            synchronized (state) {
                boolean wasPlaced = state.playerPlaced.remove(packed);
                if (!state.loadedFromPersistence) {
                    state.removedBeforeLoad.add(packed);
                    state.addedBeforeLoad.remove(packed);
                    persistDelete = true;
                } else {
                    persistDelete = wasPlaced;
                }
            }
            if (persistDelete) queueMutation(new Position(key, packed), CoalescingMutationBuffer.State.ABSENT);
        }
    }

    private void moveTracked(Collection<Block> blocks, BlockFace movement) {
        if (blocks == null || blocks.isEmpty()) return;
        for (Block source : blocks) {
            ChunkKey sourceKey = key(source.getWorld().getUID(), source.getX() >> 4, source.getZ() >> 4);
            ChunkState sourceState = chunks.computeIfAbsent(sourceKey, ignored -> new ChunkState());
            long sourcePacked = pack(source.getX(), source.getY(), source.getZ());
            boolean moveAsPlaced;
            synchronized (sourceState) {
                boolean knownPlaced = sourceState.playerPlaced.remove(sourcePacked);
                moveAsPlaced = knownPlaced || !sourceState.loadedFromPersistence;
                if (!sourceState.loadedFromPersistence) {
                    sourceState.removedBeforeLoad.add(sourcePacked);
                    sourceState.addedBeforeLoad.remove(sourcePacked);
                }
            }
            if (!moveAsPlaced) continue;
            Block destination = source.getRelative(movement);
            ChunkKey destinationKey = key(destination.getWorld().getUID(), destination.getX() >> 4, destination.getZ() >> 4);
            long destinationPacked = pack(destination.getX(), destination.getY(), destination.getZ());
            ChunkState destinationState = chunks.computeIfAbsent(destinationKey, ignored -> new ChunkState());
            synchronized (destinationState) {
                destinationState.playerPlaced.add(destinationPacked);
                if (!destinationState.loadedFromPersistence) {
                    destinationState.addedBeforeLoad.add(destinationPacked);
                    destinationState.removedBeforeLoad.remove(destinationPacked);
                }
            }
            queueMutation(new Position(sourceKey, sourcePacked), CoalescingMutationBuffer.State.ABSENT);
            queueMutation(new Position(destinationKey, destinationPacked), CoalescingMutationBuffer.State.PRESENT);
        }
    }

    private void queueMutation(Position position, CoalescingMutationBuffer.State state) {
        if (!acceptingMutations.get()) return;
        mutations.put(position, state);
        if (mutations.pendingSize() >= pressureThreshold) requestFlush();
    }

    private void requestFlush() {
        if (!persistenceReady.get() || database == null || retryStreak.get() > maxRetries) return;
        flushOnce(false);
    }

    private CompletableFuture<Void> flushOnce(boolean force) {
        SqliteDatabase db = database;
        if (db == null || !persistenceReady.get()) return CompletableFuture.completedFuture(null);
        if (!force && retryStreak.get() > maxRetries) return CompletableFuture.completedFuture(null);
        if (!flushInProgress.compareAndSet(false, true)) return activeFlush.get();

        CoalescingMutationBuffer.Batch<Position> batch = mutations.drain(batchSize);
        if (batch.empty()) {
            flushInProgress.set(false);
            activeBatchSize.set(0);
            return CompletableFuture.completedFuture(null);
        }

        activeBatchSize.set(batch.size());
        CompletableFuture<Void> gate = new CompletableFuture<>();
        activeFlush.set(gate);
        db.executeWrite(connection -> persistMutationBatch(connection, batch)).whenComplete((ignored, error) -> {
            activeBatchSize.set(0);
            flushInProgress.set(false);
            if (error == null) {
                mutations.complete(batch);
                retryStreak.set(0);
                persistenceFailure = null;
                lastSuccessfulWrite = Instant.now();
                writerState = WriterState.READY;
                gate.complete(null);
                if (!mutations.isEmpty() && acceptingMutations.get()) requestFlush();
                return;
            }

            mutations.retry(batch);
            totalRetries.incrementAndGet();
            int streak = retryStreak.incrementAndGet();
            failPersistence(error, streak > maxRetries ? WriterState.FAILED : WriterState.DEGRADED);
            gate.completeExceptionally(unwrap(error));
            if (streak <= maxRetries) scheduleRetry(streak);
        });
        return gate;
    }

    private void persistMutationBatch(java.sql.Connection connection, CoalescingMutationBuffer.Batch<Position> batch) throws Exception {
        try (PreparedStatement delete = connection.prepareStatement("DELETE FROM core_block_origin WHERE world_id=? AND chunk_x=? AND chunk_z=? AND packed_pos=?");
             PreparedStatement insert = connection.prepareStatement("INSERT OR IGNORE INTO core_block_origin(world_id,chunk_x,chunk_z,packed_pos) VALUES(?,?,?,?)")) {
            boolean hasDelete = false;
            boolean hasInsert = false;
            for (CoalescingMutationBuffer.Mutation<Position> mutation : batch.mutations()) {
                Position position = mutation.key();
                if (mutation.state() == CoalescingMutationBuffer.State.ABSENT) {
                    bind(delete, position.key, position.packed);
                    delete.addBatch();
                    hasDelete = true;
                } else {
                    bind(insert, position.key, position.packed);
                    insert.addBatch();
                    hasInsert = true;
                }
            }
            if (hasDelete) delete.executeBatch();
            if (hasInsert) insert.executeBatch();
        }
    }

    private void scheduleRetry(int streak) {
        CoreScheduler.TaskHandle current = retryTask;
        if (current != null && !current.cancelled()) current.cancel();
        long delayMillis = Math.min(5_000L, 250L << Math.min(4, Math.max(0, streak - 1)));
        retryTask = scheduler.scheduleAsync(plugin, Duration.ofMillis(delayMillis), this::requestFlush);
    }

    private void failPersistence(Throwable error, WriterState state) {
        persistenceFailure = unwrap(error);
        lastFailureAt = Instant.now();
        writerState = state;
    }

    private static void bind(PreparedStatement ps, ChunkKey key, long packed) throws Exception {
        ps.setString(1, key.worldId.toString());
        ps.setInt(2, key.chunkX);
        ps.setInt(3, key.chunkZ);
        ps.setLong(4, packed);
    }

    private static ChunkKey key(UUID worldId, int chunkX, int chunkZ) { return new ChunkKey(worldId, chunkX, chunkZ); }

    static long pack(int x, int y, int z) {
        return ((long) (x & 0x3FFFFFF) << 38) | ((long) (z & 0x3FFFFFF) << 12) | (y & 0xFFFL);
    }

    private static Throwable unwrap(Throwable error) {
        return error instanceof java.util.concurrent.CompletionException && error.getCause() != null ? error.getCause() : error;
    }

    public CloseReport closeBounded(Duration timeout) {
        acceptingMutations.set(false);
        CoreScheduler.TaskHandle periodic = periodicFlush;
        if (periodic != null && !periodic.cancelled()) periodic.cancel();
        CoreScheduler.TaskHandle retry = retryTask;
        if (retry != null && !retry.cancelled()) retry.cancel();

        Duration limit = timeout == null ? Duration.ofSeconds(4) : timeout;
        long deadline = System.nanoTime() + Math.max(1L, limit.toNanos());
        Throwable closeFailure = null;
        while (!mutations.isEmpty() && System.nanoTime() < deadline) {
            retryStreak.set(0);
            try {
                long remaining = Math.max(1L, deadline - System.nanoTime());
                flushOnce(true).get(remaining, TimeUnit.NANOSECONDS);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                closeFailure = ex;
                break;
            } catch (ExecutionException | TimeoutException ex) {
                closeFailure = unwrap(ex);
                break;
            }
        }

        int unsettled = mutations.pendingSize();
        if (unsettled > 0) {
            writerState = WriterState.FAILED;
            String detail = "BlockOrigin shutdown left " + unsettled + " unsettled mutations";
            if (closeFailure != null) detail += ": " + closeFailure.getMessage();
            safeSevere(detail);
        } else {
            writerState = WriterState.CLOSED;
        }
        persistenceReady.set(false);
        return new CloseReport(unsettled == 0, unsettled, closeFailure == null ? null : closeFailure.getMessage());
    }

    @Override
    public void close() {
        closeBounded(Duration.ofSeconds(4));
    }

    private void safeSevere(String message) {
        java.util.logging.Logger logger = plugin.getLogger();
        if (logger != null) logger.severe(message);
    }

    public record BlockPosition(int x, int y, int z) {}
    private record ChunkKey(UUID worldId, int chunkX, int chunkZ) {}
    private record Position(ChunkKey key, long packed) {}
    private static final class ChunkState {
        private final Set<Long> playerPlaced = ConcurrentHashMap.newKeySet();
        private final Set<Long> addedBeforeLoad = ConcurrentHashMap.newKeySet();
        private final Set<Long> removedBeforeLoad = ConcurrentHashMap.newKeySet();
        private volatile boolean loading;
        private volatile boolean loadedFromPersistence;
    }

    public enum WriterState { STARTING, READY, DEGRADED, FAILED, CLOSED }
    public record PersistenceStats(
            int pendingKeys,
            int activeBatchSize,
            long totalRetries,
            int retryStreak,
            Instant lastSuccessfulWrite,
            Instant lastFailureAt,
            String lastFailure,
            WriterState writerState,
            int writerConnectionOpens) {}
    public record CloseReport(boolean settled, int unsettledEntries, String failure) {}
    public record Stats(
            long cachedChunks,
            long knownChunks,
            long unknownChunks,
            long trackedPlacedBlocks,
            int pendingWrites,
            boolean persistenceReady,
            String persistenceError) {}
}
