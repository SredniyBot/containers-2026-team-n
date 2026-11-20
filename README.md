# Контейнеризация: лабораторные 1–4

Одно приложение проходит путь от Dockerfile до Kubernetes. Каждая лаба добавляет к нему что-то новое, история изменений — в [CHANGES.md](CHANGES.md), состояние на момент сдачи — в git-метках `lab1`…`lab4`.

**Команда:** _впишите участников_

## Приложение

«Заметки» — веб-страница и REST API на Java 21 без фреймворков ([app/](app)).

| Запрос | Что делает |
|---|---|
| `GET /` | страница со списком и формой |
| `GET /api/notes`, `POST /api/notes` | список в JSON; добавить заметку, тело запроса — текст |
| `GET /health/live` | процесс жив |
| `GET /health/ready` | хранилище доступно |

## Лабораторные

| ЛР | Отчёт | Файлы |
|---|---|---|
| 1. Dockerfile | [docs/lab1-dockerfile.md](docs/lab1-dockerfile.md) | [Dockerfile](Dockerfile), [Dockerfile.bad](Dockerfile.bad) |

## Быстрый старт

```bash
docker build -t notes-app:good .
docker run -d -p 8080:8080 -v notes-data:/data notes-app:good
```

Открыть http://localhost:8080.
