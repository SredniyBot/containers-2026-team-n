package notes;

/**
 * Точка входа. Первый аргумент — команда:
 * serve (по умолчанию) запускает веб-сервер.
 */
public final class Main {

    public static void main(String[] args) throws Exception {
        String command = args.length > 0 ? args[0] : "serve";
        Config config = Config.fromEnv();
        switch (command) {
            case "serve" -> Server.start(config);
            default -> {
                System.err.println("Unknown command: " + command + ". Use: serve");
                System.exit(2);
            }
        }
    }
}
