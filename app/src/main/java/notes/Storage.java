package notes;

import java.util.List;

public interface Storage {

    /** Последние заметки, новые сверху. */
    List<Note> list() throws Exception;

    Note add(String text) throws Exception;

    /** Можно ли сейчас читать и писать. Используется в /health/ready. */
    boolean ready();

    /** Короткое описание для страницы: где лежат данные. */
    String describe();
}
