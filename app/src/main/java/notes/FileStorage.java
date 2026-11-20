package notes;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Заметки в текстовом файле, по одной на строку: id, время, текст через табуляцию.
 * Файл лежит в DATA_DIR — в контейнере туда монтируется volume.
 */
public final class FileStorage implements Storage {

    private final Path dir;
    private final Path file;
    private long nextId;

    public FileStorage(Path dir) throws IOException {
        this.dir = dir;
        this.file = dir.resolve("notes.tsv");
        Files.createDirectories(dir);
        this.nextId = readAll().stream().mapToLong(Note::id).max().orElse(0) + 1;
    }

    @Override
    public synchronized List<Note> list() throws IOException {
        List<Note> notes = readAll();
        Collections.reverse(notes);
        return notes.size() > 100 ? notes.subList(0, 100) : notes;
    }

    @Override
    public synchronized Note add(String text) throws IOException {
        Note note = new Note(nextId++, text, Instant.now());
        String line = note.id() + "\t" + note.createdAt() + "\t" + escape(text) + "\n";
        Files.writeString(file, line, StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.APPEND, StandardOpenOption.SYNC);
        return note;
    }

    @Override
    public boolean ready() {
        return Files.isDirectory(dir) && Files.isWritable(dir);
    }

    @Override
    public String describe() {
        return "file " + file;
    }

    private List<Note> readAll() throws IOException {
        List<Note> notes = new ArrayList<>();
        if (!Files.exists(file)) {
            return notes;
        }
        for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
            String[] parts = line.split("\t", 3);
            if (parts.length == 3) {
                notes.add(new Note(Long.parseLong(parts[0]), unescape(parts[2]), Instant.parse(parts[1])));
            }
        }
        return notes;
    }

    private static String escape(String s) {
        return s.replace("\\", "\\\\").replace("\t", "\\t").replace("\n", "\\n").replace("\r", "");
    }

    private static String unescape(String s) {
        StringBuilder out = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '\\' && i + 1 < s.length()) {
                char next = s.charAt(++i);
                out.append(switch (next) {
                    case 't' -> '\t';
                    case 'n' -> '\n';
                    default -> next;
                });
            } else {
                out.append(c);
            }
        }
        return out.toString();
    }
}
