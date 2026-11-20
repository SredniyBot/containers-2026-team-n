package notes;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * Заметки в PostgreSQL. Таблицу создаёт команда migrate,
 * сервер её только читает и пишет.
 */
public final class PostgresStorage implements Storage {

    private final Config config;

    public PostgresStorage(Config config) {
        this.config = config;
        DriverManager.setLoginTimeout(3);
    }

    @Override
    public List<Note> list() throws SQLException {
        try (Connection c = connect();
             PreparedStatement st = c.prepareStatement(
                     "SELECT id, text, created_at FROM notes ORDER BY id DESC LIMIT 100");
             ResultSet rs = st.executeQuery()) {
            List<Note> notes = new ArrayList<>();
            while (rs.next()) {
                notes.add(new Note(rs.getLong(1), rs.getString(2), rs.getTimestamp(3).toInstant()));
            }
            return notes;
        }
    }

    @Override
    public Note add(String text) throws SQLException {
        try (Connection c = connect();
             PreparedStatement st = c.prepareStatement(
                     "INSERT INTO notes (text) VALUES (?) RETURNING id, created_at")) {
            st.setString(1, text);
            try (ResultSet rs = st.executeQuery()) {
                rs.next();
                return new Note(rs.getLong(1), text, rs.getTimestamp(2).toInstant());
            }
        }
    }

    /** Готовы, если база отвечает и миграция уже создала таблицу. */
    @Override
    public boolean ready() {
        try (Connection c = connect();
             PreparedStatement st = c.prepareStatement("SELECT 1 FROM notes LIMIT 1")) {
            st.setQueryTimeout(2);
            st.executeQuery().close();
            return true;
        } catch (SQLException e) {
            return false;
        }
    }

    @Override
    public String describe() {
        return "postgres " + config.dbHost() + ":" + config.dbPort() + "/" + config.dbName();
    }

    private Connection connect() throws SQLException {
        return DriverManager.getConnection(config.jdbcUrl(), config.dbUser(), config.dbPassword());
    }
}
