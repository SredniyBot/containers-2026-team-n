package notes;

import java.util.List;
import java.util.stream.Collectors;

/** Ручная сериализация: ради двух полей не стоит тянуть библиотеку. */
final class Json {

    static String notes(List<Note> notes) {
        return notes.stream().map(Json::note).collect(Collectors.joining(",", "[", "]"));
    }

    static String note(Note n) {
        return "{\"id\":" + n.id()
                + ",\"text\":" + string(n.text())
                + ",\"createdAt\":" + string(n.createdAt().toString()) + "}";
    }

    private static String string(String s) {
        StringBuilder out = new StringBuilder("\"");
        for (char c : s.toCharArray()) {
            switch (c) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> {
                    if (c < 0x20) {
                        out.append(String.format("\\u%04x", (int) c));
                    } else {
                        out.append(c);
                    }
                }
            }
        }
        return out.append('"').toString();
    }
}
