package notes;

import java.time.Instant;

public record Note(long id, String text, Instant createdAt) {
}
