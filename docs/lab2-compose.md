# ЛР 2. Docker Compose

Приложение из ЛР 1 теперь хранит заметки в PostgreSQL. [docker-compose.yml](../docker-compose.yml) поднимает три сервиса в общей сети: базу `db`, одноразовый `migrate`, который создаёт схему и завершается, и приложение `app`. Значения переменных лежат в [.env](../.env).

## Запуск

```bash
docker compose up -d --build
docker compose ps -a
```

Ожидаемое состояние:

```
NAME            STATUS                      PORTS
notes-app       Up 27 seconds (healthy)     0.0.0.0:8080->8080/tcp
notes-db        Up 2 minutes (healthy)      5432/tcp
notes-migrate   Exited (0) 30 seconds ago
```

`Exited (0)` у `migrate` — это норма: он сделал работу и вышел. Приложение открывается на http://localhost:8080.

![Приложение под Compose](img/lab2-app.png)

Остановить, сохранив данные: `docker compose down`. Удалить вместе с базой: `docker compose down -v`.

## Как сервисы запускаются по очереди

```
db ──healthy──▶ migrate ──exit 0──▶ app
```

1. `db` стартует. Пока `pg_isready` не ответит, Compose считает его `starting`.
2. `migrate` ждёт `service_healthy` у `db`. Команда `migrate` создаёт таблицу и первую заметку «Схему создала команда migrate», затем завершается с кодом 0.
3. `app` ждёт `service_completed_successfully` у `migrate`. Если миграция упадёт, приложение не запустится и не будет отвечать ошибками на пустую базу.

Схему создаёт отдельная команда, а не сервер при старте. Так сервер не нужны права на изменение схемы, а когда реплик несколько, миграция всё равно выполняется один раз. В ЛР 4 та же команда станет init-контейнером.

## Требования → где выполнено

| Требование | Где |
|---|---|
| Минимум 1 init + 2 app-сервиса | `migrate` ([стр. 29](../docker-compose.yml#L29)) + `db` и `app` |
| Сборка образа из соседнего Dockerfile и имя образа | `build` + `image: notes-app:2.0` ([стр. 44–47](../docker-compose.yml#L44-L47)) |
| Жёсткие имена контейнеров | `container_name`: `notes-db`, `notes-migrate`, `notes-app` |
| `depends_on` | `migrate` → `db`, `app` → `db` и `migrate`, с условиями ([стр. 53–57](../docker-compose.yml#L53-L57)) |
| volume | именованный `notes-db-data` для данных PostgreSQL ([стр. 12](../docker-compose.yml#L12)) |
| Проброс порта наружу | `${APP_PORT}:8080` ([стр. 52](../docker-compose.yml#L52)) |
| `command` и/или `entrypoint` | `command: ["migrate"]` и `["serve"]`. ENTRYPOINT с `java -jar` берётся из Dockerfile, команды меняются только аргументом |
| healthcheck | `db`: `pg_isready`, `app`: `/health/ready` |
| Все переменные в `.env` | в compose-файле нет ни одного значения, только `env_file: .env` и `${APP_PORT}` |
| Явная общая сеть | `notes-net` ([стр. 77–80](../docker-compose.yml#L77-L80)), все три сервиса подключены к ней |

Сверх требований: лимиты CPU и памяти у `db` и `app` (о них первый вопрос) и `restart: unless-stopped` у приложения.

### Заметки к решениям

- **Строки `version:` нет.** Compose v2 её игнорирует и выдаёт предупреждение, что поле устарело.
- **`pull_policy: never` у `migrate`.** Этот сервис использует образ, который собирает `app`. Строка запрещает Compose искать `notes-app:2.0` в Docker Hub: такого образа там нет.
- **`.env` в репозитории.** Так требует задание. В рабочем проекте этот файл добавляют в `.gitignore`, а в репозиторий кладут `.env.example` с пустыми значениями.
- **`start_period: 120s` у базы.** При первом запуске PostgreSQL создаёт кластер. На нашем HDD это занимало от 40 секунд до двух минут, смотря по нагрузке на диск: одна только запись при завершении initdb однажды длилась 28 секунд. С `retries: 10` и без `start_period` Compose успевал объявить базу `unhealthy`, и остальные сервисы не запускались. Неудачные проверки в течение `start_period` не считаются, а как только база ответила, она сразу становится `healthy`.

## Ответы на вопросы

### Можно ли ограничить CPU и память сервиса?

Можно. В Compose v2 лимиты задаются в `deploy.resources.limits`:

```yaml
deploy:
  resources:
    limits:
      cpus: "1"
      memory: 384M
```

Раньше `deploy` работал только в Docker Swarm, а для обычного `docker compose up` использовали `mem_limit` и `cpus`. Сейчас Compose применяет `deploy.resources` и без Swarm. Старые ключи тоже поддерживаются. Резерв задаётся рядом, в `reservations`.

Проверили, что лимиты действительно применились к контейнерам:

```
$ docker inspect notes-app --format '{{.HostConfig.Memory}} {{.HostConfig.NanoCpus}}'
402653184 1000000000          # 384 МиБ и 1 CPU

$ docker stats --no-stream
notes-db    19.14MiB / 256MiB
notes-app   39.23MiB / 384MiB
```

Для Java лимит важен ещё и потому, что JVM подстраивает под него размер кучи. С флагом `-XX:MaxRAMPercentage=75` из Dockerfile максимальная куча в этом контейнере — 288 МиБ, то есть 75 % от 384 МиБ, а не от памяти хоста. Без лимита JVM ориентировалась бы на всю память машины, и контейнер мог бы забрать её у соседей.

### Как запустить только один сервис?

Указать имя сервиса:

```bash
docker compose up -d db
```

Запустится только `db`. Но если у сервиса есть `depends_on`, Compose поднимет и зависимости: `docker compose up -d app` запустит все три сервиса. Чтобы зависимости не трогать, есть флаг `--no-deps`:

```bash
docker compose up -d --no-deps app
```

Мы проверили оба варианта: после `up -d db` работал один контейнер, после `up -d --no-deps app` к нему добавился только `notes-app`, без `migrate`.

Одноразовую задачу удобно запускать через `run`: контейнер создаётся заново и удаляется после выхода.

```
$ docker compose run --rm migrate
connected to jdbc:postgresql://db:5432/notes
migration done
```

Ещё один способ — профили (`profiles:` у сервиса). Такой сервис запускается, только если его профиль указан явно: `docker compose --profile tools up`.
