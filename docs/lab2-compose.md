# ЛР 2. Docker Compose

Приложение из ЛР 1 теперь хранит заметки в PostgreSQL. [docker-compose.yml](../docker-compose.yml) поднимает в общей сети три сервиса: базу `db`, приложение `app` и одноразовый `migrate`, который создаёт схему и завершается. Переменные вынесены в [.env](../.env).

## Запуск

```bash
docker compose up -d --build
docker compose ps -a
```

Должно получиться так:

```
NAME            STATUS                      PORTS
notes-app       Up 27 seconds (healthy)     0.0.0.0:8080->8080/tcp
notes-db        Up 2 minutes (healthy)      5432/tcp
notes-migrate   Exited (0) 30 seconds ago
```

У `migrate` статус `Exited (0)`, так и должно быть: он создал схему и вышел. Приложение открывается на http://localhost:8080.

![Приложение под Compose](img/lab2-app.png)

`docker compose down` останавливает сервисы и сохраняет данные, `docker compose down -v` удаляет ещё и базу. Если Docker Hub недоступен, `postgres:16.16-alpine` нужно заранее скачать с зеркала, как в [ЛР 1](lab1-dockerfile.md#запуск).

## Порядок запуска

```
db ──healthy──▶ migrate ──exit 0──▶ app
```

1. Первым стартует `db`. Пока `pg_isready` не отвечает, Compose считает его `starting`.
2. `migrate` ждёт, пока `db` станет `healthy` (`service_healthy`), создаёт таблицу с первой заметкой «Схему создала команда migrate» и завершается с кодом 0.
3. `app` стартует после успешного завершения `migrate` (`service_completed_successfully`). Если миграция упадёт, приложение не запустится и не будет сыпать ошибками на пустой базе.

Если бы схему создавал сервер при старте, это делала бы каждая реплика. Отдельный `migrate` в Compose выполняется один раз, сколько бы реплик ни было. Заодно серверу можно выдать пользователя базы без права менять схему, хотя у нас для простоты пользователь один. В ЛР 4 та же команда стала init-контейнером, а он есть в каждом поде, так что там понадобилась блокировка.

## Пункты задания

| Пункт | Где сделано |
|---|---|
| Минимум 1 init + 2 app-сервиса | `migrate` ([стр. 29](../docker-compose.yml#L29)), `db` и `app` |
| Сборка образа из соседнего Dockerfile и имя образа | `build` и `image: notes-app:2.0` ([стр. 44–47](../docker-compose.yml#L44-L47)) |
| Жёсткие имена контейнеров | `container_name`: `notes-db`, `notes-migrate`, `notes-app` |
| `depends_on` | `migrate` ждёт `db` ([стр. 35–37](../docker-compose.yml#L35-L37)), `app` ждёт `db` и `migrate` ([стр. 53–57](../docker-compose.yml#L53-L57)), везде с условиями |
| volume | именованный `notes-db-data` для данных PostgreSQL ([стр. 12](../docker-compose.yml#L12)) |
| Проброс порта наружу | `${APP_PORT}:8080` ([стр. 52](../docker-compose.yml#L52)) |
| `command` и/или `entrypoint` | `command: ["migrate"]` и `["serve"]`. ENTRYPOINT с `java -jar` берётся из Dockerfile, команды отличаются только аргументом |
| healthcheck | у `db` `pg_isready`, у `app` `/health/ready` |
| Все переменные в `.env` | значения переменных окружения в compose-файле не записаны. В контейнеры они попадают через `env_file: .env`, а порт хоста `${APP_PORT}` Compose подставляет из того же `.env` |
| Явная общая сеть | `notes-net` ([стр. 77–80](../docker-compose.yml#L77-L80)), к ней подключены все три сервиса |

Сверх задания добавили лимиты CPU и памяти у `db` и `app` (про них первый вопрос) и `restart: unless-stopped` у приложения.

### Пояснения к файлу

Строки `version:` нет: Compose v2 её игнорирует и предупреждает, что поле устарело.

У `migrate` стоит `pull_policy: never`. Этот сервис запускает образ, который собирает `app`, и без этой строки Compose пошёл бы искать `notes-app:2.0` в Docker Hub, где такого образа нет.

`.env` лежит в репозитории, потому что этого требует задание. В рабочем проекте его вносят в `.gitignore`, а в репозиторий кладут `.env.example` с пустыми значениями.

У базы `start_period: 120s`. При первом запуске PostgreSQL создаёт кластер, и на нашем HDD это занимало от 40 секунд до двух минут, смотря по нагрузке на диск. В первой версии файла было `retries: 10` без `start_period`. Compose успевал объявить базу `unhealthy`, и остальные сервисы не запускались. Неудачные проверки в течение `start_period` не считаются, а как только база ответит, она сразу станет `healthy`.

## Ответы на вопросы

### Можно ли ограничить CPU и память сервиса?

Можно, через `deploy.resources.limits`. У приложения так:

```yaml
deploy:
  resources:
    limits:
      cpus: "1"
      memory: 384M
```

Когда-то `deploy` учитывался только в Docker Swarm, а для обычного `docker compose up` писали `mem_limit` и `cpus`. Нынешний Compose применяет `deploy.resources` и без Swarm, старые ключи тоже понимает. Резерв задаётся рядом, в `reservations`.

Проверили, что лимиты применились:

```
$ docker inspect notes-app --format '{{.HostConfig.Memory}} {{.HostConfig.NanoCpus}}'
402653184 1000000000          # 384 МиБ и 1 CPU

$ docker stats --no-stream
notes-db    19.14MiB / 256MiB
notes-app   39.23MiB / 384MiB
```

Для Java лимит важен ещё и потому, что JVM считает от него размер кучи. С флагом `-XX:MaxRAMPercentage=75` из Dockerfile куча в этом контейнере может вырасти до 288 МиБ, это 75 % от 384 МиБ. Без лимита JVM считала бы от всей памяти машины и могла бы отнять её у соседних контейнеров.

### Как запустить только один сервис?

Назвать его в команде:

```bash
docker compose up -d db
```

Запустится только `db`. С `app` так не выйдет: у него есть `depends_on`, и `docker compose up -d app` поднимет все три сервиса. Чтобы зависимости не запускались, нужен флаг `--no-deps`:

```bash
docker compose up -d --no-deps app
```

После `up -d db` у нас работал один контейнер, после `up -d --no-deps app` к нему добавился только `notes-app`, без `migrate`.

Одноразовую задачу удобнее запускать через `run`. Он создаёт новый контейнер, а с `--rm` удаляет его после выхода:

```
$ docker compose run --rm migrate
2026-11-20T20:19:14.925532569Z connected to jdbc:postgresql://db:5432/notes
2026-11-20T20:19:14.945997355Z migration done
```

Есть ещё профили. Сервис с `profiles: [tools]` не запускается по `docker compose up`, пока профиль не указан явно: `docker compose --profile tools up`.
