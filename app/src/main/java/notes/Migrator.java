package notes;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Команда migrate: дождаться базы, создать схему и выйти.
 * Запускается одноразово: init-сервисом в Compose, init-контейнером в Kubernetes.
 */
public final class Migrator {

    /** Произвольный номер advisory-блокировки: две миграции не пойдут одновременно. */
    private static final long LOCK_ID = 4242;

    public static void run(Config config) throws Exception {
        String sql;
        try (InputStream in = Migrator.class.getResourceAsStream("/schema.sql")) {
            sql = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }

        DriverManager.setLoginTimeout(3);
        try (Connection c = waitForDatabase(config)) {
            c.setAutoCommit(false);
            try (Statement st = c.createStatement()) {
                st.execute("SELECT pg_advisory_xact_lock(" + LOCK_ID + ")");
                st.execute(sql);
            }
            c.commit();
        }
        Server.log("migration done");
    }

    private static Connection waitForDatabase(Config config) throws Exception {
        long deadline = System.currentTimeMillis() + config.migrateTimeoutSeconds() * 1000L;
        while (true) {
            try {
                Connection c = DriverManager.getConnection(config.jdbcUrl(), config.dbUser(), config.dbPassword());
                Server.log("connected to " + config.jdbcUrl());
                return c;
            } catch (SQLException e) {
                if (System.currentTimeMillis() > deadline) {
                    throw new IllegalStateException("database is not available after "
                            + config.migrateTimeoutSeconds() + " s", e);
                }
                Server.log("waiting for database: " + e.getMessage());
                Thread.sleep(2000);
            }
        }
    }
}
