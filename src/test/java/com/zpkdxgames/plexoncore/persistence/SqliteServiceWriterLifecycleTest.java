package com.zpkdxgames.plexoncore.persistence;

import com.zpkdxgames.plexoncore.scheduler.CoreScheduler;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.Proxy;
import java.nio.file.Path;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

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
