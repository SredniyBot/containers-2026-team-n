# ЛР 2. Docker Compose

Приложение из ЛР 1 перешло на PostgreSQL. [docker-compose.yml](../docker-compose.yml) поднимает базу `db`, одноразовый `migrate`, который создаёт схему, и приложение `app`. Переменные лежат в [.env](../.env).

## Запуск

```bash
docker compose up -d --build
docker compose ps -a
```

```
NAME            STATUS                      PORTS
notes-app       Up 27 seconds (healthy)     0.0.0.0:8080->8080/tcp
notes-db        Up 2 minutes (healthy)      5432/tcp
notes-migrate   Exited (0) 30 seconds ago
```

`migrate` создал схему и вышел с кодом 0. Приложение открывается на http://localhost:8080, `docker compose down -v` удаляет всё вместе с базой.

![Приложение под Compose](img/lab2-app.png)

## Порядок запуска

```
db ──healthy──▶ migrate ──exit 0──▶ app
```

`migrate` ждёт, пока база станет `healthy`, `app` ждёт успешного завершения `migrate`. Если миграция упадёт, приложение не запустится. Отдельный сервис создаёт схему один раз, сколько бы реплик приложения ни было.

## Пункты задания

| Пункт | Где сделано |
|---|---|
| Минимум 1 init + 2 app-сервиса | `migrate` ([стр. 29](../docker-compose.yml#L29)), `db` и `app` |
| Сборка образа из соседнего Dockerfile и имя образа | `build` и `image: notes-app:2.0` ([стр. 44–47](../docker-compose.yml#L44-L47)) |
| Жёсткие имена контейнеров | `container_name`: `notes-db`, `notes-migrate`, `notes-app` |
| `depends_on` | `migrate` ждёт `db` ([стр. 35–37](../docker-compose.yml#L35-L37)), `app` ждёт `db` и `migrate` ([стр. 53–57](../docker-compose.yml#L53-L57)) |
| volume | `notes-db-data` для данных PostgreSQL ([стр. 12](../docker-compose.yml#L12)) |
| Проброс порта наружу | `${APP_PORT}:8080` ([стр. 52](../docker-compose.yml#L52)) |
| `command` и/или `entrypoint` | `command: ["migrate"]` и `["serve"]` при общем ENTRYPOINT из Dockerfile |
| healthcheck | у `db` `pg_isready`, у `app` `/health/ready` |
| Все переменные в `.env` | контейнеры получают их через `env_file: .env`, `${APP_PORT}` Compose подставляет оттуда же |
| Явная общая сеть | `notes-net` ([стр. 77–80](../docker-compose.yml#L77-L80)) |

Сверх задания: лимиты CPU и памяти, `restart: unless-stopped` у приложения.

У базы `start_period: 120s`. На нашем HDD первая инициализация PostgreSQL шла до двух минут, и без этого Compose объявлял базу `unhealthy`. `.env` лежит в git по заданию, в рабочем проекте вместо него коммитят `.env.example`.

## Ответы на вопросы

### Можно ли ограничить CPU и память сервиса?

Да, в `deploy.resources.limits`. В старом формате для этого были `mem_limit` и `cpus`.

```yaml
deploy:
  resources:
    limits:
      cpus: "1"
      memory: 384M
```

```
$ docker inspect notes-app --format '{{.HostConfig.Memory}} {{.HostConfig.NanoCpus}}'
402653184 1000000000          # 384 МиБ и 1 CPU
```

JVM считает размер кучи от этого лимита. С `-XX:MaxRAMPercentage=75` из Dockerfile куча может вырасти до 288 МиБ.

### Как запустить только один сервис?

`docker compose up -d db`. Если у сервиса есть `depends_on`, Compose поднимет и зависимости, а флаг `--no-deps` это отключает: `docker compose up -d --no-deps app`. Одноразовую задачу удобно запускать через `docker compose run --rm migrate`.
