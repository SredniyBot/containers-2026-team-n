# Контейнеризация: лабораторные 1–4

В ЛР 1, 2 и 4 одно и то же приложение проходит путь от Dockerfile до Kubernetes. ЛР 3 сделана на Nextcloud из методички. Что менялось от работы к работе, записано в [CHANGES.md](CHANGES.md). Код после каждой работы отмечен git-метками `lab1`…`lab4`, а окончательные тексты отчётов лежат в ветке `main`.

**Команда:** _впишите участников_

## Лабораторные

| ЛР | Отчёт | Файлы | Что внутри |
|---|---|---|---|
| 1. Dockerfile | [docs/lab1-dockerfile.md](docs/lab1-dockerfile.md) | [Dockerfile](Dockerfile), [Dockerfile.bad](Dockerfile.bad) | 6 плохих практик и их исправление. Образ уменьшился с 660 до 208 МБ, остановка проходит по SIGTERM, процесс работает не от root |
| 2. Docker Compose | [docs/lab2-compose.md](docs/lab2-compose.md) | [docker-compose.yml](docker-compose.yml), [.env](.env) | База, init-сервис миграции и приложение запускаются по очереди. Healthcheck, лимиты ресурсов, ответы на 2 вопроса |
| 3. Kubernetes | [docs/lab3-kubernetes.md](docs/lab3-kubernetes.md) | [lab3-nextcloud/](lab3-nextcloud) | Nextcloud и PostgreSQL в minikube: Secret, ConfigMap, пробы, два эксперимента по вопросам |
| 4. Kubernetes, своё приложение | [docs/lab4-kubernetes.md](docs/lab4-kubernetes.md) | [k8s/](k8s) | Две реплики, init-контейнер, PVC, метки, запуск не от root |

## Приложение

«Заметки» — веб-страница и REST API на Java 21 без фреймворков ([app/](app)). Из одного образа запускается и сервер (команда `serve`), и миграция (команда `migrate`): она создаёт схему в базе и завершается.

| Запрос | Что делает |
|---|---|
| `GET /` | страница со списком и формой |
| `GET /api/notes`, `POST /api/notes` | список в JSON; добавить заметку (тело запроса — текст) |
| `GET /health/live` | процесс жив |
| `GET /health/ready` | хранилище доступно |

Хранилище выбирается переменной `STORAGE`: `file` пишет в `/data/notes.tsv` (ЛР 1), `postgres` — в базу (ЛР 2 и 4).

## Быстрый старт

```bash
docker compose up -d --build
```

Открыть http://localhost:8080.

Если Docker Hub недоступен, базовые образы и `postgres:16.16-alpine` можно взять с `mirror.gcr.io`, как описано в [отчёте ЛР 1](docs/lab1-dockerfile.md#запуск).
