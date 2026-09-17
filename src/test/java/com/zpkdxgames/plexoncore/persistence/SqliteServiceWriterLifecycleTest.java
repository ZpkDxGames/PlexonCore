package com.zpkdxgames.plexoncore.persistence;

import com.zpkdxgames.plexoncore.scheduler.CoreScheduler;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.Proxy;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SqliteServiceWriterLifecycleTest {
    @TempDir Path tempDir;

    @Test
    void samePathSharesOneAuthorityAndReusesWriterConnection() throws Exception {
        CoreScheduler scheduler = new CoreScheduler(plugin(), 1, 64, 1, 64);
        SqliteService service = new SqliteService(scheduler);
        Path databasePath = tempDir.resolve("writer.db");
        try {
            var database = service.open(databasePath);
            database.executeWrite(connection -> connection.createStatement().execute("CREATE TABLE sample(id INTEGER PRIMARY KEY, value TEXT)")).get();
            database.executeWrite(connection -> connection.createStatement().execute("INSERT INTO sample(value) VALUES('one')")).get();

            assertEquals(1, database.writerConnectionOpenCount());
            assertSame(database, service.open(databasePath));
            assertEquals(1, service.openDatabaseCount());
        } finally {
            service.close();
            scheduler.close();
        }
    }

    @Test
    void failedWriteRollsBackMarksFailureAndLaterWriteRecoversHealth() throws Exception {
        CoreScheduler scheduler = new CoreScheduler(plugin(), 1, 64, 1, 64);
        SqliteService service = new SqliteService(scheduler);
        try {
            var database = service.open(tempDir.resolve("recovery.db"));
            database.executeWrite(connection -> connection.createStatement().execute("CREATE TABLE sample(id INTEGER PRIMARY KEY, value TEXT)")).get();

            assertThrows(ExecutionException.class, () -> database.executeWrite(connection -> {
                connection.createStatement().execute("INSERT INTO sample(value) VALUES('rolled-back')");
                throw new IllegalStateException("synthetic write failure");
            }).get());
            assertEquals(SqliteService.HealthState.FAILED, database.health().state());

            database.executeWrite(connection -> connection.createStatement().execute("INSERT INTO sample(value) VALUES('recovered')")).get();
            assertEquals(SqliteService.HealthState.HEALTHY, database.health().state());
            int count = database.query(connection -> {
                try (var statement = connection.createStatement();
                     var result = statement.executeQuery("SELECT COUNT(*) FROM sample")) {
                    return result.next() ? result.getInt(1) : -1;
                } catch (Exception error) {
                    throw new IllegalStateException(error);
                }
            }).get();
            assertEquals(1, count, "failed transaction must not leak its insert");
        } finally {
            service.close();
            scheduler.close();
        }
    }

    @Test
    void migrationIsIdempotentAndFutureSchemaIsRejected() throws Exception {
        CoreScheduler scheduler = new CoreScheduler(plugin(), 1, 64, 1, 64);
        SqliteService service = new SqliteService(scheduler);
        try {
            var database = service.open(tempDir.resolve("migration.db"));
            var v1 = new SqliteService.Migration(1, "one", connection ->
                    connection.createStatement().execute("CREATE TABLE IF NOT EXISTS v1(id INTEGER PRIMARY KEY)"));
            var v2 = new SqliteService.Migration(2, "two", connection ->
                    connection.createStatement().execute("CREATE TABLE IF NOT EXISTS v2(id INTEGER PRIMARY KEY)"));

            assertEquals(2, database.migrate(List.of(v1, v2)).get());
            assertEquals(2, database.migrate(List.of(v1, v2)).get());
            assertThrows(ExecutionException.class, () -> database.migrate(List.of(v1)).get());
            assertEquals(SqliteService.HealthState.FAILED, database.health().state());
        } finally {
            service.close();
            scheduler.close();
        }
    }

    @Test
    void boundedCloseSettlesWriterAndRejectsNewWrites() throws Exception {
        CoreScheduler scheduler = new CoreScheduler(plugin(), 1, 64, 1, 64);
        SqliteService service = new SqliteService(scheduler);
        try {
            var database = service.open(tempDir.resolve("close.db"));
            database.executeWrite(connection -> connection.createStatement().execute("CREATE TABLE sample(id INTEGER PRIMARY KEY)")).get();

            var report = database.closeBounded(Duration.ofSeconds(1));

            assertTrue(report.settled());
            assertTrue(database.closed());
            assertThrows(ExecutionException.class, () -> database.executeWrite(connection -> {}).get());
        } finally {
            service.close();
            scheduler.close();
        }
    }

    private static Plugin plugin() {
        Logger logger = Logger.getLogger("SqliteServiceWriterLifecycleTest");
        return (Plugin) Proxy.newProxyInstance(Plugin.class.getClassLoader(), new Class<?>[]{Plugin.class}, (proxy, method, args) -> switch (method.getName()) {
            case "getName" -> "TestCore";
            case "isEnabled" -> true;
            case "getLogger" -> logger;
            case "hashCode" -> System.identityHashCode(proxy);
            case "equals" -> proxy == args[0];
            default -> defaultValue(method.getReturnType());
        });
    }

    private static Object defaultValue(Class<?> type) {
        if (!type.isPrimitive()) return null;
        if (type == boolean.class) return false;
        if (type == byte.class) return (byte) 0;
        if (type == short.class) return (short) 0;
        if (type == int.class) return 0;
        if (type == long.class) return 0L;
        if (type == float.class) return 0F;
        if (type == double.class) return 0D;
        if (type == char.class) return '\0';
        return null;
    }
}
