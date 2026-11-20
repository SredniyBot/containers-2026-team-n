package notes;

import java.nio.file.Path;

/** Настройки приложения. Все значения приходят из переменных окружения. */
public record Config(int port, Path dataDir) {

    public static Config fromEnv() {
        return new Config(
                Integer.parseInt(env("PORT", "8080")),
                Path.of(env("DATA_DIR", "/data")));
    }

    static String env(String name, String fallback) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? fallback : value;
    }
}
