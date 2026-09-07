package com.zpkdxgames.plexoncore.persistence;

import com.zpkdxgames.plexoncore.scheduler.CoreScheduler;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

public final class SqliteService implements AutoCloseable {
    private final CoreScheduler scheduler;
    private final List<SqliteDatabase> openDatabases = java.util.Collections.synchronizedList(new ArrayList<>());

    public SqliteService(CoreScheduler scheduler) {
        this.scheduler = Objects.requireNonNull(scheduler);
    }

    public SqliteDatabase open(Path databaseFile) throws SQLException, IOException {
        Path path = databaseFile.toAbsolutePath().normalize();
        if (path.getParent() != null) Files.createDirectories(path.getParent());
        SqliteDatabase database = new SqliteDatabase(path, scheduler);
        database.verify();
        openDatabases.add(database);
        return database;
    }

    @Override
    public void close() {
        synchronized (openDatabases) {
            for (SqliteDatabase database : openDatabases) database.close();
            openDatabases.clear();
        }
    }

    public static final class SqliteDatabase implements AutoCloseable {
        private final Path path;
        private final String jdbcUrl;
        private final CoreScheduler scheduler;
        private final AsyncWriteQueue writer;
        private volatile DatabaseHealth health = new DatabaseHealth(HealthState.UNKNOWN, "Not checked", Instant.now(), 0);

        private SqliteDatabase(Path path, CoreScheduler scheduler) {
            this.path = path;
            this.jdbcUrl = "jdbc:sqlite:" + path;
            this.scheduler = scheduler;
            this.writer = new AsyncWriteQueue("PlexonCore-SQLite-" + sanitize(path.getFileName().toString()), 4096);
        }

        public CompletableFuture<Void> executeWrite(SqlWork work) {
            return writer.<Void>submit(() -> {
                try (Connection connection = connection()) {
                    connection.setAutoCommit(false);
                    try {
                        work.run(connection);
                        connection.commit();
                    } catch (Exception ex) {
                        connection.rollback();
                        throw ex;
                    }
                }
                return null;
            }).whenComplete((ignored, error) -> updateHealth(error));
        }

        public <T> CompletableFuture<T> query(Function<Connection, T> query) {
            return scheduler.supplyAsync(() -> {
                try (Connection connection = connection()) {
                    return query.apply(connection);
                } catch (SQLException ex) {
                    throw new IllegalStateException("SQLite query failed for " + path.getFileName(), ex);
                }
            }).whenComplete((ignored, error) -> updateHealth(error));
        }

        public CompletableFuture<Integer> migrate(List<Migration> migrations) {
            List<Migration> ordered = migrations == null ? List.of() : migrations.stream().sorted(java.util.Comparator.comparingInt(Migration::version)).toList();
            return writer.submit(() -> {
                try (Connection connection = connection()) {
                    ensureMigrationTable(connection);
                    int current = currentVersion(connection);
                    connection.setAutoCommit(false);
                    try {
                        for (Migration migration : ordered) {
                            if (migration.version() <= current) continue;
                            migration.work().run(connection);
                            try (PreparedStatement ps = connection.prepareStatement("INSERT INTO plexon_schema(version, applied_at) VALUES(?, ?)")) {
                                ps.setInt(1, migration.version());
                                ps.setString(2, Instant.now().toString());
                                ps.executeUpdate();
                            }
                            current = migration.version();
                        }
                        connection.commit();
                        return current;
                    } catch (Exception ex) {
                        connection.rollback();
                        throw ex;
                    }
                }
            }).whenComplete((ignored, error) -> updateHealth(error));
        }

        public Path backup(Path destination) throws IOException, SQLException {
            checkpoint();
            Path normalized = destination.toAbsolutePath().normalize();
            if (normalized.getParent() != null) Files.createDirectories(normalized.getParent());
            Files.copy(path, normalized, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES);
            return normalized;
        }

        public void checkpoint() throws SQLException {
            try (Connection connection = connection(); Statement statement = connection.createStatement()) {
                statement.execute("PRAGMA wal_checkpoint(TRUNCATE)");
            }
        }

        public DatabaseHealth health() { return health; }
        public Path path() { return path; }
        public int queuedWrites() { return writer.queueSize(); }

        private void verify() throws SQLException {
            try (Connection connection = connection(); Statement statement = connection.createStatement(); ResultSet rs = statement.executeQuery("PRAGMA journal_mode")) {
                String mode = rs.next() ? rs.getString(1) : "unknown";
                health = new DatabaseHealth(mode.equalsIgnoreCase("wal") ? HealthState.HEALTHY : HealthState.DEGRADED,
                    "journal_mode=" + mode, Instant.now(), writer.queueSize());
            }
        }

        private Connection connection() throws SQLException {
            Connection connection = DriverManager.getConnection(jdbcUrl);
            try (Statement statement = connection.createStatement()) {
                statement.execute("PRAGMA journal_mode=WAL");
                statement.execute("PRAGMA foreign_keys=ON");
                statement.execute("PRAGMA busy_timeout=5000");
                statement.execute("PRAGMA synchronous=NORMAL");
            }
            return connection;
        }

        private static void ensureMigrationTable(Connection connection) throws SQLException {
            try (Statement statement = connection.createStatement()) {
                statement.execute("CREATE TABLE IF NOT EXISTS plexon_schema(version INTEGER PRIMARY KEY, applied_at TEXT NOT NULL)");
            }
        }

        private static int currentVersion(Connection connection) throws SQLException {
            try (Statement statement = connection.createStatement(); ResultSet rs = statement.executeQuery("SELECT COALESCE(MAX(version), 0) FROM plexon_schema")) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        }

        private void updateHealth(Throwable error) {
            Throwable root = error instanceof java.util.concurrent.CompletionException && error.getCause() != null ? error.getCause() : error;
            health = root == null
                ? new DatabaseHealth(HealthState.HEALTHY, "Ready", Instant.now(), writer.queueSize())
                : new DatabaseHealth(HealthState.FAILED, root.getMessage(), Instant.now(), writer.queueSize());
        }

        @Override public void close() { writer.close(); }
    }

    public static final class AsyncWriteQueue implements AutoCloseable {
        private final ThreadPoolExecutor executor;

        private AsyncWriteQueue(String name, int capacity) {
            AtomicInteger sequence = new AtomicInteger();
            ThreadFactory factory = runnable -> {
                Thread thread = new Thread(runnable, name + "-" + sequence.incrementAndGet());
                thread.setDaemon(true);
                return thread;
            };
            this.executor = new ThreadPoolExecutor(1, 1, 30, TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(Math.max(64, capacity)), factory, new ThreadPoolExecutor.AbortPolicy());
        }

        public <T> CompletableFuture<T> submit(java.util.concurrent.Callable<T> task) {
            CompletableFuture<T> future = new CompletableFuture<>();
            try {
                executor.execute(() -> {
                    try { future.complete(task.call()); }
                    catch (Throwable ex) { future.completeExceptionally(ex); }
                });
            } catch (java.util.concurrent.RejectedExecutionException ex) {
                future.completeExceptionally(ex);
            }
            return future;
        }

        public int queueSize() { return executor.getQueue().size(); }

        @Override public void close() {
            executor.shutdown();
            try {
                if (!executor.awaitTermination(5, TimeUnit.SECONDS)) executor.shutdownNow();
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                executor.shutdownNow();
            }
        }
    }

    @FunctionalInterface public interface SqlWork { void run(Connection connection) throws Exception; }
    public record Migration(int version, String description, SqlWork work) {
        public Migration {
            if (version < 1) throw new IllegalArgumentException("Migration version must be positive");
            description = description == null ? "" : description;
            Objects.requireNonNull(work);
        }
    }
    public enum HealthState { HEALTHY, DEGRADED, FAILED, UNKNOWN }
    public record DatabaseHealth(HealthState state, String detail, Instant checkedAt, int queuedWrites) {}

    private static String sanitize(String name) { return name.replaceAll("[^A-Za-z0-9._-]", "_"); }
}
