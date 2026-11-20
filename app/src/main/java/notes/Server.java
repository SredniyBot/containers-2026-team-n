package notes;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;

/**
 * GET  /              страница со списком и формой
 * POST /notes         добавить заметку из формы, затем редирект на /
 * GET  /api/notes     список в JSON
 * POST /api/notes     добавить заметку, тело запроса — текст
 * GET  /health/live   процесс жив
 * GET  /health/ready  хранилище доступно
 */
public final class Server {

    private static final DateTimeFormatter TIME =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss 'UTC'").withZone(ZoneOffset.UTC);

    private final Storage storage;
    private final String host = Config.env("HOSTNAME", "unknown");

    private Server(Storage storage) {
        this.storage = storage;
    }

    public static void start(Config config) throws Exception {
        Storage storage = switch (config.storage()) {
            case "file" -> new FileStorage(config.dataDir());
            case "postgres" -> new PostgresStorage(config);
            default -> throw new IllegalArgumentException("STORAGE must be file or postgres");
        };
        Server app = new Server(storage);

        HttpServer http = HttpServer.create(new InetSocketAddress(config.port()), 0);
        http.createContext("/", app::handle);
        http.setExecutor(Executors.newVirtualThreadPerTaskExecutor());
        http.start();
        log("listening on :" + config.port() + ", storage: " + storage.describe());

        // SIGTERM от docker stop / kubelet: дождаться текущих запросов и выйти.
        CountDownLatch stopped = new CountDownLatch(1);
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            log("SIGTERM received, stopping");
            http.stop(1);
            log("stopped");
            stopped.countDown();
        }));
        stopped.await();
    }

    private void handle(HttpExchange ex) throws IOException {
        String method = ex.getRequestMethod();
        String path = ex.getRequestURI().getPath();
        int status;
        try {
            status = route(ex, method, path);
        } catch (Exception e) {
            log("error: " + e);
            status = send(ex, 500, "text/plain", "internal error: " + e.getMessage());
        }
        if (!path.startsWith("/health")) {
            log(method + " " + path + " " + status);
        }
        ex.close();
    }

    private int route(HttpExchange ex, String method, String path) throws Exception {
        return switch (method + " " + path) {
            case "GET /" -> send(ex, 200, "text/html", page(storage.list()));
            case "POST /notes" -> {
                String text = formField(body(ex), "text");
                if (!text.isBlank()) {
                    storage.add(text.strip());
                }
                ex.getResponseHeaders().add("Location", "/");
                yield send(ex, 303, "text/plain", "");
            }
            case "GET /api/notes" -> send(ex, 200, "application/json", Json.notes(storage.list()));
            case "POST /api/notes" -> {
                String text = body(ex).strip();
                if (text.isEmpty() || text.length() > 1000) {
                    yield send(ex, 400, "text/plain", "text must be 1..1000 characters");
                }
                yield send(ex, 201, "application/json", Json.note(storage.add(text)));
            }
            case "GET /health/live" -> send(ex, 200, "text/plain", "ok");
            case "GET /health/ready" -> storage.ready()
                    ? send(ex, 200, "text/plain", "ready")
                    : send(ex, 503, "text/plain", "storage unavailable");
            default -> send(ex, 404, "text/plain", "not found");
        };
    }

    private String page(List<Note> notes) {
        StringBuilder items = new StringBuilder();
        for (Note n : notes) {
            items.append("<li><span>").append(TIME.format(n.createdAt())).append("</span>")
                    .append(html(n.text())).append("</li>");
        }
        if (notes.isEmpty()) {
            items.append("<li class=empty>Заметок пока нет</li>");
        }
        return """
                <!doctype html>
                <html lang="ru"><head><meta charset="utf-8">
                <meta name="viewport" content="width=device-width, initial-scale=1">
                <title>Заметки</title>
                <style>
                  body { font: 16px/1.5 system-ui, sans-serif; max-width: 640px; margin: 40px auto; padding: 0 16px; color: #1d1d1f; }
                  form { display: flex; gap: 8px; margin: 16px 0 24px; }
                  input { flex: 1; padding: 8px 10px; font: inherit; border: 1px solid #c7c7cc; border-radius: 6px; }
                  button { padding: 8px 16px; font: inherit; border: 0; border-radius: 6px; background: #0a66c2; color: #fff; }
                  ul { list-style: none; padding: 0; } li { padding: 10px 0; border-bottom: 1px solid #e5e5ea; }
                  li span { display: block; font-size: 12px; color: #6e6e73; } .empty { color: #6e6e73; }
                  footer { margin-top: 32px; font-size: 13px; color: #6e6e73; }
                </style></head><body>
                <h1>Заметки</h1>
                <form method="post" action="/notes">
                  <input name="text" maxlength="1000" placeholder="Новая заметка" required autofocus>
                  <button>Добавить</button>
                </form>
                <ul>%s</ul>
                <footer>Ответил контейнер <b>%s</b> · хранилище: %s</footer>
                </body></html>
                """.formatted(items, html(host), html(storage.describe()));
    }

    private static String body(HttpExchange ex) throws IOException {
        byte[] bytes = ex.getRequestBody().readNBytes(16 * 1024);
        return new String(bytes, StandardCharsets.UTF_8);
    }

    private static String formField(String body, String name) {
        for (String pair : body.split("&")) {
            int eq = pair.indexOf('=');
            if (eq > 0 && pair.substring(0, eq).equals(name)) {
                return URLDecoder.decode(pair.substring(eq + 1), StandardCharsets.UTF_8);
            }
        }
        return "";
    }

    private static int send(HttpExchange ex, int status, String type, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", type + "; charset=utf-8");
        ex.sendResponseHeaders(status, bytes.length == 0 ? -1 : bytes.length);
        if (bytes.length > 0) {
            try (OutputStream out = ex.getResponseBody()) {
                out.write(bytes);
            }
        }
        return status;
    }

    private static String html(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }

    static void log(String message) {
        System.out.println(java.time.Instant.now() + " " + message);
    }
}
