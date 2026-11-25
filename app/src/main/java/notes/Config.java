package notes;

import java.nio.file.Path;

/** Настройки приложения. Все значения приходят из переменных окружения. */
public record Config(
        int port,
        String storage,
        Path dataDir,
        String dbHost,
        int dbPort,
        String dbName,
        String dbUser,
        String dbPassword,
        int migrateTimeoutSeconds) {

    public static Config fromEnv() {
        return new Config(
                Integer.parseInt(env("PORT", "8080")),
                env("STORAGE", "file"),
                Path.of(env("DATA_DIR", "/data")),
                env("DB_HOST", "localhost"),
                Integer.parseInt(env("DB_PORT", "5432")),
                env("DB_NAME", env("POSTGRES_DB", "notes")),
                env("DB_USER", env("POSTGRES_USER", "notes")),
                env("DB_PASSWORD", env("POSTGRES_PASSWORD", "")),
                Integer.parseInt(env("MIGRATE_TIMEOUT", "60")));
    }

    public String jdbcUrl() {
        return "jdbc:postgresql://" + dbHost + ":" + dbPort + "/" + dbName;
    }

    static String env(String name, String fallback) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? fallback : value;
    }
}
