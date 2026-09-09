package com.zpkdxgames.plexoncore.origin;

import com.zpkdxgames.plexoncore.context.BlockOrigin;
import com.zpkdxgames.plexoncore.persistence.SqliteService;
import com.zpkdxgames.plexoncore.persistence.SqliteService.Migration;
import com.zpkdxgames.plexoncore.persistence.SqliteService.SqliteDatabase;
import com.zpkdxgames.plexoncore.scheduler.CoreScheduler;
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

import java.nio.file.Path;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

public final class BlockOriginService implements Listener {
    private final Plugin plugin;
    private final CoreScheduler scheduler;
    private final Map<ChunkKey, ChunkState> chunks = new ConcurrentHashMap<>();
    private final AtomicBoolean persistenceReady = new AtomicBoolean();
    private volatile SqliteDatabase database;
    private volatile Throwable persistenceFailure;

    public BlockOriginService(Plugin plugin, CoreScheduler scheduler) {
        this.plugin = Objects.requireNonNull(plugin);
        this.scheduler = Objects.requireNonNull(scheduler);
    }

    public void start(SqliteService sqlite, Path databasePath) {
        try {
            database = sqlite.open(databasePath);
            database.migrate(List.of(new Migration(200, "Core block origin index", connection -> {
                try (var statement = connection.createStatement()) {
                    statement.execute("CREATE TABLE IF NOT EXISTS core_block_origin(world_id TEXT NOT NULL, chunk_x INTEGER NOT NULL, chunk_z INTEGER NOT NULL, packed_pos INTEGER NOT NULL, PRIMARY KEY(world_id, chunk_x, chunk_z, packed_pos))");
                    statement.execute("CREATE INDEX IF NOT EXISTS idx_core_block_origin_chunk ON core_block_origin(world_id, chunk_x, chunk_z)");
                }
            }))).whenComplete((version, error) -> {
                if (error != null) {
                    persistenceFailure = unwrap(error);
                    plugin.getLogger().severe("Block origin persistence unavailable: " + persistenceFailure.getMessage());
                    return;
                }
                persistenceReady.set(true);
                scheduler.runPrimary(this::loadCurrentlyLoadedChunks);
            });
        } catch (Exception error) {
            persistenceFailure = error;
            plugin.getLogger().severe("Block origin database could not be opened: " + error.getMessage());
        }
    }

    public BlockOrigin origin(Block block) {
        ChunkKey key = key(block.getWorld().getUID(), block.getChunk().getX(), block.getChunk().getZ());
        ChunkState state = chunks.get(key);
        if (state == null) return BlockOrigin.UNKNOWN;
        long packed = pack(block.getX(), block.getY(), block.getZ());
        if (state.playerPlaced.contains(packed)) return BlockOrigin.PLAYER_PLACED;
        return state.loadedFromPersistence ? BlockOrigin.NATURAL : BlockOrigin.UNKNOWN;
    }

    public Stats stats() {
        long tracked = chunks.values().stream().mapToLong(state -> state.playerPlaced.size()).sum();
        long loaded = chunks.values().stream().filter(state -> state.loadedFromPersistence).count();
        return new Stats(chunks.size(), loaded, chunks.size() - loaded, tracked,
            database == null ? 0 : database.queuedWrites(), persistenceReady.get(), persistenceFailure == null ? null : persistenceFailure.getMessage());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        markPlaced(event.getBlockPlaced());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        removeTracked(List.of(event.getBlock()));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBurn(BlockBurnEvent event) {
        removeTracked(List.of(event.getBlock()));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onFade(BlockFadeEvent event) {
        removeTracked(List.of(event.getBlock()));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        removeTracked(event.blockList());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        removeTracked(event.blockList());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPistonExtend(BlockPistonExtendEvent event) {
        moveTracked(event.getBlocks(), event.getDirection());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPistonRetract(BlockPistonRetractEvent event) {
        moveTracked(event.getBlocks(), event.getDirection().getOppositeFace());
    }

    @EventHandler
    public void onChunkLoad(ChunkLoadEvent event) {
        loadChunk(event.getChunk());
    }

    @EventHandler
    public void onChunkUnload(ChunkUnloadEvent event) {
        chunks.remove(key(event.getWorld().getUID(), event.getChunk().getX(), event.getChunk().getZ()));
    }

    private void loadCurrentlyLoadedChunks() {
        for (World world : Bukkit.getWorlds()) {
            for (Chunk chunk : world.getLoadedChunks()) loadChunk(chunk);
        }
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
            state.loading = false;
            if (error != null) {
                persistenceFailure = unwrap(error);
                return;
            }
            state.playerPlaced.addAll(positions);
            state.loadedFromPersistence = true;
        });
    }

    private void markPlaced(Block block) {
        ChunkKey key = key(block.getWorld().getUID(), block.getChunk().getX(), block.getChunk().getZ());
        long packed = pack(block.getX(), block.getY(), block.getZ());
        chunks.computeIfAbsent(key, ignored -> new ChunkState()).playerPlaced.add(packed);
        SqliteDatabase db = database;
        if (db == null || !persistenceReady.get()) return;
        db.executeWrite(connection -> {
            try (PreparedStatement ps = connection.prepareStatement("INSERT OR IGNORE INTO core_block_origin(world_id,chunk_x,chunk_z,packed_pos) VALUES(?,?,?,?)")) {
                bind(ps, key, packed);
                ps.executeUpdate();
            }
        });
    }

    private void removeTracked(Collection<Block> blocks) {
        if (blocks == null || blocks.isEmpty()) return;
        List<Position> removed = new ArrayList<>();
        for (Block block : blocks) {
            ChunkKey key = key(block.getWorld().getUID(), block.getChunk().getX(), block.getChunk().getZ());
            ChunkState state = chunks.get(key);
            if (state == null) continue;
            long packed = pack(block.getX(), block.getY(), block.getZ());
            if (state.playerPlaced.remove(packed)) removed.add(new Position(key, packed));
        }
        deletePositions(removed);
    }

    private void moveTracked(Collection<Block> blocks, BlockFace movement) {
        if (blocks == null || blocks.isEmpty()) return;
        List<Move> moves = new ArrayList<>();
        for (Block source : blocks) {
            ChunkKey sourceKey = key(source.getWorld().getUID(), source.getChunk().getX(), source.getChunk().getZ());
            ChunkState sourceState = chunks.get(sourceKey);
            if (sourceState == null) continue;
            long sourcePacked = pack(source.getX(), source.getY(), source.getZ());
            if (!sourceState.playerPlaced.remove(sourcePacked)) continue;
            Block destination = source.getRelative(movement);
            ChunkKey destinationKey = key(destination.getWorld().getUID(), destination.getChunk().getX(), destination.getChunk().getZ());
            long destinationPacked = pack(destination.getX(), destination.getY(), destination.getZ());
            chunks.computeIfAbsent(destinationKey, ignored -> new ChunkState()).playerPlaced.add(destinationPacked);
            moves.add(new Move(new Position(sourceKey, sourcePacked), new Position(destinationKey, destinationPacked)));
        }
        SqliteDatabase db = database;
        if (moves.isEmpty() || db == null || !persistenceReady.get()) return;
        db.executeWrite(connection -> {
            try (PreparedStatement delete = connection.prepareStatement("DELETE FROM core_block_origin WHERE world_id=? AND chunk_x=? AND chunk_z=? AND packed_pos=?");
                 PreparedStatement insert = connection.prepareStatement("INSERT OR IGNORE INTO core_block_origin(world_id,chunk_x,chunk_z,packed_pos) VALUES(?,?,?,?)")) {
                for (Move move : moves) {
                    bind(delete, move.from.key, move.from.packed); delete.addBatch();
                    bind(insert, move.to.key, move.to.packed); insert.addBatch();
                }
                delete.executeBatch();
                insert.executeBatch();
            }
        });
    }

    private void deletePositions(List<Position> positions) {
        SqliteDatabase db = database;
        if (positions.isEmpty() || db == null || !persistenceReady.get()) return;
        db.executeWrite(connection -> {
            try (PreparedStatement ps = connection.prepareStatement("DELETE FROM core_block_origin WHERE world_id=? AND chunk_x=? AND chunk_z=? AND packed_pos=?")) {
                for (Position position : positions) { bind(ps, position.key, position.packed); ps.addBatch(); }
                ps.executeBatch();
            }
        });
    }

    private static void bind(PreparedStatement ps, ChunkKey key, long packed) throws Exception {
        ps.setString(1, key.worldId.toString());
        ps.setInt(2, key.chunkX);
        ps.setInt(3, key.chunkZ);
        ps.setLong(4, packed);
    }

    private static ChunkKey key(UUID worldId, int chunkX, int chunkZ) {
        return new ChunkKey(worldId, chunkX, chunkZ);
    }

    static long pack(int x, int y, int z) {
        return ((long) (x & 0x3FFFFFF) << 38) | ((long) (z & 0x3FFFFFF) << 12) | (y & 0xFFFL);
    }

    private static Throwable unwrap(Throwable error) {
        return error instanceof java.util.concurrent.CompletionException && error.getCause() != null ? error.getCause() : error;
    }

    private record ChunkKey(UUID worldId, int chunkX, int chunkZ) {}
    private record Position(ChunkKey key, long packed) {}
    private record Move(Position from, Position to) {}
    private static final class ChunkState {
        private final Set<Long> playerPlaced = ConcurrentHashMap.newKeySet();
        private volatile boolean loading;
        private volatile boolean loadedFromPersistence;
    }

    public record Stats(long cachedChunks, long knownChunks, long unknownChunks, long trackedPlacedBlocks,
                        int pendingWrites, boolean persistenceReady, String persistenceError) {}
}
