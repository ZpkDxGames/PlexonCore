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
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

public final class SqliteService implements AutoCloseable {
    private final CoreScheduler scheduler;
    private final Map<Path, SqliteDatabase> openDatabases = new LinkedHashMap<>();

    public SqliteService(CoreScheduler scheduler) { this.scheduler = Objects.requireNonNull(scheduler); }

    /**
     * Returns one writer authority per normalized database path. Reopening the same path reuses the
     * existing database object instead of creating a competing SQLite writer queue.
     */
    public synchronized SqliteDatabase open(Path databaseFile) throws SQLException, IOException {
        Path path = databaseFile.toAbsolutePath().normalize();
        if (path.getParent() != null) Files.createDirectories(path.getParent());
        SqliteDatabase existing = openDatabases.get(path);
        if (existing != null && !existing.closed()) return existing;
        SqliteDatabase database = new SqliteDatabase(path, scheduler);
        database.verify();
        openDatabases.put(path, database);
        return database;
    }

    public synchronized int openDatabaseCount() {
        return (int) openDatabases.values().stream().filter(database -> !database.closed()).count();
    }

    @Override
    public synchronized void close() {
        for (SqliteDatabase database : openDatabases.values()) database.close();
        openDatabases.clear();
    }

    public static final class SqliteDatabase implements AutoCloseable {
        private final Path path;
        private final String jdbcUrl;
        private final CoreScheduler scheduler;
        private final PersistentWriteQueue writer;
        private final AtomicBoolean closed = new AtomicBoolean();
        private volatile DatabaseHealth health = new DatabaseHealth(HealthState.UNKNOWN, "Not checked", Instant.now(), 0);

        private SqliteDatabase(Path path, CoreScheduler scheduler) {
            this.path = path;
            this.jdbcUrl = "jdbc:sqlite:" + path;
            this.scheduler = scheduler;
            this.writer = new PersistentWriteQueue("PlexonCore-SQLite-" + sanitize(path.getFileName().toString()), jdbcUrl, 4096);
        }

        public CompletableFuture<Void> executeWrite(SqlWork work) {
            Objects.requireNonNull(work, "work");
            if (closed.get()) return CompletableFuture.failedFuture(new IllegalStateException("SQLite database is closed: " + path.getFileName()));
            return writer.<Void>submit(connection -> {
                boolean previousAutoCommit = connection.getAutoCommit();
                connection.setAutoCommit(false);
                try {
                    work.run(connection);
                    connection.commit();
                } catch (Exception ex) {
                    connection.rollback();
                    throw ex;
                } finally {
                    connection.setAutoCommit(previousAutoCommit);
                }
                return null;
            }).whenComplete((ignored, error) -> updateHealth(error));
        }

        public <T> CompletableFuture<T> query(Function<Connection, T> query) {
            Objects.requireNonNull(query, "query");
            if (closed.get()) return CompletableFuture.failedFuture(new IllegalStateException("SQLite database is closed: " + path.getFileName()));
            return scheduler.supplyIo(() -> {
                try (Connection connection = readConnection()) {
                    return query.apply(connection);
                } catch (SQLException ex) {
                    throw new IllegalStateException("SQLite query failed for " + path.getFileName(), ex);
                }
            }).whenComplete((ignored, error) -> updateHealth(error));
        }

        public CompletableFuture<Integer> migrate(List<Migration> migrations) {
            List<Migration> ordered = migrations == null ? List.of() : migrations.stream()
                    .sorted(Comparator.comparingInt(Migration::version)).toList();
            if (closed.get()) return CompletableFuture.failedFuture(new IllegalStateException("SQLite database is closed: " + path.getFileName()));
            return writer.submit(connection -> {
                ensureMigrationTable(connection);
                int current = currentVersion(connection);
                if (!ordered.isEmpty()) {
                    int highestKnown = ordered.getLast().version();
                    if (current > highestKnown) {
                        throw new IllegalStateException("Database schema " + current + " is newer than supported schema " + highestKnown + " for " + path.getFileName());
                    }
                }
                boolean previousAutoCommit = connection.getAutoCommit();
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
                } finally {
                    connection.setAutoCommit(previousAutoCommit);
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
            if (closed.get()) throw new SQLException("SQLite database is closed: " + path.getFileName());
            try {
                writer.submit(connection -> {
                    try (Statement statement = connection.createStatement()) {
                        statement.execute("PRAGMA wal_checkpoint(TRUNCATE)");
                    }
                    return null;
                }).get(5, TimeUnit.SECONDS);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                throw new SQLException("Interrupted while checkpointing " + path.getFileName(), ex);
            } catch (ExecutionException | TimeoutException ex) {
                throw new SQLException("Could not checkpoint " + path.getFileName(), ex);
            }
        }

        public DatabaseHealth health() { return health; }
        public Path path() { return path; }
        public int queuedWrites() { return writer.queueSize(); }
        public int writerConnectionOpenCount() { return writer.connectionOpenCount(); }
        public boolean closed() { return closed.get(); }

        private void verify() throws SQLException {
            try (Connection connection = configuredConnection(); Statement statement = connection.createStatement(); ResultSet rs = statement.executeQuery("PRAGMA journal_mode")) {
                String mode = rs.next() ? rs.getString(1) : "unknown";
                health = new DatabaseHealth(mode.equalsIgnoreCase("wal") ? HealthState.HEALTHY : HealthState.DEGRADED,
                        "journal_mode=" + mode, Instant.now(), writer.queueSize());
            }
        }

        private Connection configuredConnection() throws SQLException {
            Connection connection = DriverManager.getConnection(jdbcUrl);
            configureConnection(connection, true);
            return connection;
        }

        private Connection readConnection() throws SQLException {
            Connection connection = DriverManager.getConnection(jdbcUrl);
            configureConnection(connection, false);
            return connection;
        }

        private static void configureConnection(Connection connection, boolean configureJournal) throws SQLException {
            try (Statement statement = connection.createStatement()) {
                if (configureJournal) statement.execute("PRAGMA journal_mode=WAL");
                statement.execute("PRAGMA foreign_keys=ON");
                statement.execute("PRAGMA busy_timeout=5000");
                statement.execute("PRAGMA synchronous=NORMAL");
            }
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
            Throwable root = unwrap(error);
            health = root == null
                    ? new DatabaseHealth(HealthState.HEALTHY, "Ready", Instant.now(), writer.queueSize())
                    : new DatabaseHealth(HealthState.FAILED,
                        root.getMessage() == null ? root.getClass().getSimpleName() : root.getMessage(), Instant.now(), writer.queueSize());
        }

        public CloseReport closeBounded(Duration timeout) {
            if (!closed.compareAndSet(false, true)) return new CloseReport(true, 0);
            return writer.closeBounded(timeout == null ? Duration.ofSeconds(5) : timeout);
        }

        @Override public void close() { closeBounded(Duration.ofSeconds(5)); }
    }

    /** Single-thread ordered writer with one persistent SQLite connection for its lifecycle. */
    static final class PersistentWriteQueue {
        private final ThreadPoolExecutor executor;
        private final String jdbcUrl;
        private final AtomicInteger connectionOpens = new AtomicInteger();
        private volatile Connection connection;

        private PersistentWriteQueue(String name, String jdbcUrl, int capacity) {
            this.jdbcUrl = Objects.requireNonNull(jdbcUrl);
            AtomicInteger sequence = new AtomicInteger();
            ThreadFactory factory = runnable -> {
                Thread thread = new Thread(runnable, name + "-" + sequence.incrementAndGet());
                thread.setDaemon(true);
                return thread;
            };
            this.executor = new ThreadPoolExecutor(1, 1, 30, TimeUnit.SECONDS,
                    new LinkedBlockingQueue<>(Math.max(64, capacity)), factory, new ThreadPoolExecutor.AbortPolicy());
        }

        <T> CompletableFuture<T> submit(ConnectionCallable<T> task) {
            CompletableFuture<T> future = new CompletableFuture<>();
            try {
                executor.execute(() -> {
                    try {
                        future.complete(task.call(connection()));
                    } catch (Throwable ex) {
                        future.completeExceptionally(ex);
                    }
                });
            } catch (java.util.concurrent.RejectedExecutionException ex) {
                future.completeExceptionally(ex);
            }
            return future;
        }

        private Connection connection() throws SQLException {
            Connection current = connection;
            if (current != null && !current.isClosed()) return current;
            current = DriverManager.getConnection(jdbcUrl);
            SqliteDatabase.configureConnection(current, true);
            connection = current;
            connectionOpens.incrementAndGet();
            return current;
        }

        int queueSize() { return executor.getQueue().size(); }
        int connectionOpenCount() { return connectionOpens.get(); }

        CloseReport closeBounded(Duration timeout) {
            executor.shutdown();
            long millis = Math.max(1L, timeout.toMillis());
            boolean settled = false;
            try {
                settled = executor.awaitTermination(millis, TimeUnit.MILLISECONDS);
                if (!settled) executor.shutdownNow();
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                executor.shutdownNow();
            } finally {
                Connection current = connection;
                connection = null;
                if (current != null) {
                    try { current.close(); } catch (SQLException ignored) { }
                }
            }
            return new CloseReport(settled, executor.getQueue().size());
        }
    }

    @FunctionalInterface public interface SqlWork { void run(Connection connection) throws Exception; }
    @FunctionalInterface interface ConnectionCallable<T> { T call(Connection connection) throws Exception; }
    public record Migration(int version, String description, SqlWork work) {
        public Migration {
            if (version < 1) throw new IllegalArgumentException("Migration version must be positive");
            description = description == null ? "" : description;
            Objects.requireNonNull(work);
        }
    }
    public enum HealthState { HEALTHY, DEGRADED, FAILED, UNKNOWN }
    public record DatabaseHealth(HealthState state, String detail, Instant checkedAt, int queuedWrites) {}
    public record CloseReport(boolean settled, int unsettledWrites) {}

    private static Throwable unwrap(Throwable error) {
        if (error == null) return null;
        return error instanceof java.util.concurrent.CompletionException && error.getCause() != null ? error.getCause() : error;
    }

    private static String sanitize(String name) { return name.replaceAll("[^A-Za-z0-9._-]", "_"); }
}
