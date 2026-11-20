# Изменения

## ЛР 1 — Dockerfile (метка `lab1`)

- Приложение «Заметки»: Java 21, встроенный HTTP-сервер, заметки в файле `/data/notes.tsv`.
- `Dockerfile` и `Dockerfile.bad`, отчёт [docs/lab1-dockerfile.md](docs/lab1-dockerfile.md).

## ЛР 2 — Docker Compose (метка `lab2`)

- Приложение умеет хранить заметки в PostgreSQL (`STORAGE=postgres`), файловое хранилище осталось для запуска без базы.
- Новая команда образа `migrate`: ждёт базу, создаёт схему и завершается.
- В jar вошёл драйвер PostgreSQL (maven-shade-plugin).
- `docker-compose.yml` и `.env`, отчёт [docs/lab2-compose.md](docs/lab2-compose.md).
