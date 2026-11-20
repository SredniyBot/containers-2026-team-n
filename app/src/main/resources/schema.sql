-- Схема хранилища. Скрипт можно запускать повторно.
CREATE TABLE IF NOT EXISTS notes (
    id          BIGSERIAL PRIMARY KEY,
    text        TEXT        NOT NULL CHECK (length(text) BETWEEN 1 AND 1000),
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Первая заметка показывает, что миграция отработала.
INSERT INTO notes (text)
SELECT 'Схему создала команда migrate'
WHERE NOT EXISTS (SELECT 1 FROM notes);
