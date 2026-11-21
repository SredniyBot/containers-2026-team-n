# ЛР 4. Своё приложение в Kubernetes

Перенесли связку из ЛР 2 в minikube: PostgreSQL и приложение «Заметки» в двух репликах. Роль сервиса `migrate` здесь играет init-контейнер. Манифесты лежат в [k8s/](../k8s), всё создаётся в пространстве имён `notes`.

## Запуск

Образ приложения собираем из [Dockerfile](../Dockerfile) и загружаем в minikube. Из реестра его не скачать: он нигде не опубликован. Образ Postgres загружаем так же, чтобы не зависеть от Docker Hub.

```bash
docker build -t notes-app:2.0 .
minikube image load notes-app:2.0
minikube image load postgres:16.16-alpine

kubectl apply -f k8s/
kubectl -n notes rollout status deploy/notes-app
minikube service notes-app -n notes --url
```

Последняя команда открывает туннель и печатает адрес вида `http://127.0.0.1:52512`. Порт каждый раз случайный, а терминал с туннелем закрывать нельзя. Дальше в отчёте адрес взят из нашего запуска.

Файлы пронумерованы, потому что `kubectl apply -f k8s/` идёт по алфавиту, а Namespace должен появиться первым. Удалить всё разом: `kubectl delete namespace notes`.

```
$ kubectl -n notes get deploy,pods,svc,pvc
NAME                        READY   UP-TO-DATE   AVAILABLE   AGE
deployment.apps/notes-app   2/2     2            2           80s
deployment.apps/notes-db    1/1     1            1           81s

NAME                            READY   STATUS    RESTARTS   AGE
pod/notes-app-ff98b99d8-6c5mm   1/1     Running   0          80s
pod/notes-app-ff98b99d8-zhcwg   1/1     Running   0          80s
pod/notes-db-7dd8c8c4bc-hx9sx   1/1     Running   0          47s

NAME                TYPE        CLUSTER-IP       EXTERNAL-IP   PORT(S)        AGE
service/notes-app   NodePort    10.108.100.217   <none>        80:30681/TCP   80s
service/notes-db    ClusterIP   10.98.71.91      <none>        5432/TCP       81s

NAME                                  STATUS   VOLUME                                     CAPACITY   ACCESS MODES   STORAGECLASS
persistentvolumeclaim/notes-db-data   Bound    pvc-4282984a-27f0-4140-9c3f-41ee9655bdf7   1Gi        RWO            standard
```

![Приложение в Kubernetes](img/lab4-app.png)

## Устройство

```
                 Service notes-app (NodePort)
                    │               │
          ┌─────────┴───┐       ┌───┴─────────┐
          │ pod app #1  │       │ pod app #2  │   Deployment notes-app
          │ init:migrate│       │ init:migrate│
          │ app: serve  │       │ app: serve  │
          └──────┬──────┘       └──────┬──────┘
                 └──────┬──────────────┘
               Service notes-db (ClusterIP)
                        │
                 ┌──────┴──────┐
                 │ pod postgres│──── PVC notes-db-data (1Gi)
                 └─────────────┘     Deployment notes-db
```

| Файл | Что внутри |
|---|---|
| [00-namespace.yml](../k8s/00-namespace.yml) | пространство имён `notes` |
| [10-config.yml](../k8s/10-config.yml) | ConfigMap `notes-config` (имя базы, хост, режим хранилища) и Secret `notes-db-secret` (логин и пароль) |
| [20-db.yml](../k8s/20-db.yml) | PVC, Deployment и Service базы |
| [30-app.yml](../k8s/30-app.yml) | Deployment приложения с init-контейнером и Service |

## Требования → где выполнено

| Требование | Где |
|---|---|
| Минимум два Deployment | `notes-db` и `notes-app` |
| Кастомный образ хотя бы для одного | `notes-app:2.0`, собран из нашего Dockerfile и загружен в minikube через `minikube image load` |
| Контейнер + init-контейнер в одном Deployment | `notes-app`: init `migrate` и основной `app` |
| Volume | PVC `notes-db-data` смонтирован в `/var/lib/postgresql/data` |
| ConfigMap и/или Secret | оба: `notes-config` и `notes-db-secret` |
| Service | `notes-db` (ClusterIP) и `notes-app` (NodePort) |
| Liveness / Readiness | у приложения обе, у базы `pg_isready` и TCP-проверка |
| Метки сверх selector | `part-of` на всех объектах, `component` на объектах базы и приложения, `version` на Deployment и подах |

## Ход работы и проверки

### Init-контейнер ждёт базу и создаёт схему

В Kubernetes нет `depends_on`, и поды приложения стартуют одновременно с базой. Init-контейнер `migrate` — это тот же образ с командой `migrate`. Он повторяет подключение, пока база не ответит, затем создаёт схему и завершается. Основной контейнер запускается только после его успешного выхода.

```
$ kubectl -n notes logs notes-app-ff98b99d8-6c5mm -c migrate
2026-11-20T23:57:04.602955941Z waiting for database: Connection to notes-db:5432 refused. …
2026-11-20T23:57:06.608019819Z waiting for database: Connection to notes-db:5432 refused. …
2026-11-20T23:57:08.608958640Z waiting for database: Connection to notes-db:5432 refused. …
2026-11-20T23:57:11.208359864Z connected to jdbc:postgresql://notes-db:5432/notes
2026-11-20T23:57:11.349433024Z migration done
```

`MIGRATE_TIMEOUT` в ConfigMap — 180 секунд, а не 60, как в Compose. Первая инициализация базы на новом томе у нас доходила до двух минут (см. ЛР 2), и init-контейнер не должен сдаться раньше.

Реплик две, поэтому миграция запустилась дважды почти одновременно. Вторая не мешает первой: перед изменением схемы команда берёт блокировку `pg_advisory_xact_lock`, а SQL-скрипт можно выполнять повторно. В итоге в базе одна таблица и одна стартовая заметка.

### Данные переживают удаление пода базы

Удалили под Postgres. Deployment создал новый, тот подключил тот же PVC, и заметки остались на месте:

```
$ kubectl -n notes delete pod -l app.kubernetes.io/name=notes-db
$ curl http://127.0.0.1:52512/api/notes
[{"id":2,"text":"Заметка из Kubernetes",...},{"id":1,"text":"Схему создала команда migrate",...}]
```

В ЛР 3 похожий опыт с Postgres без тома удалил 131 таблицу Nextcloud.

Несколько решений по базе:

- `strategy: Recreate`: при обновлении старый под останавливается раньше, чем стартует новый, и два Postgres не пишут в один том одновременно.
- `PGDATA` указывает на подкаталог `pgdata`. В корне тома может оказаться служебный `lost+found`, и тогда initdb откажется работать с непустым каталогом.
- Deployment, а не StatefulSet. StatefulSet нужен, когда у реплик базы свои тома и постоянные имена, например при репликации. У нас реплика одна, и Deployment с PVC и `Recreate` справляется.

### Пробы

Пока база перезапускалась, приложение отвечало на `/health/ready` кодом 503, readiness-проба не проходила, и Kubernetes временно убрал поды из Service. Liveness при этом проходила, поэтому поды не перезапускались. После восстановления базы оба пода снова готовы, счётчик перезапусков нулевой:

```
$ kubectl -n notes get events          # вывод сокращён
Warning  Unhealthy  pod/notes-app-ff98b99d8-zhcwg  Readiness probe failed: HTTP probe failed with statuscode: 503
Warning  Unhealthy  pod/notes-app-ff98b99d8-6c5mm  Readiness probe failed: HTTP probe failed with statuscode: 503

$ kubectl -n notes get pods -l app.kubernetes.io/name=notes-app
NAME                        READY   STATUS    RESTARTS   AGE
notes-app-ff98b99d8-6c5mm   1/1     Running   0          50s
notes-app-ff98b99d8-zhcwg   1/1     Running   0          50s
```

Так и задумано: `/health/live` проверяет только процесс, `/health/ready` — ещё и доступ к базе. Если бы liveness зависела от базы, сбой Postgres вызывал бы цепочку бесполезных перезапусков приложения.

### Две реплики за одним Service

На странице выводится имя пода, который ответил. Из 10 запросов подряд 2 обработал один под и 8 другой. Service распределяет новые соединения между готовыми подами.

### Метки

Используем общепринятые метки из документации Kubernetes, их же ставят Helm-чарты. На Deployment, подах, Service и PVC стоят `app.kubernetes.io/name`, `component` (database или backend) и `part-of: notes`, на Deployment и подах ещё `version`. На Namespace, ConfigMap и Secret — только `part-of`. Метки позволяют выбирать объекты по смыслу, а не по имени:

```
$ kubectl -n notes get all -l app.kubernetes.io/component=database -o name
pod/notes-db-7dd8c8c4bc-c4hpn
service/notes-db
deployment.apps/notes-db
replicaset.apps/notes-db-7dd8c8c4bc

$ kubectl -n notes get deploy,svc,pvc,cm,secret -l app.kubernetes.io/part-of=notes
```

Selector у Deployment и Service опирается только на `app.kubernetes.io/name`. Selector Deployment после создания менять нельзя, поэтому `version` в него не включаем: иначе смена версии потребовала бы пересоздать Deployment.

### Запуск не от root

В шаблоне пода приложения указано `securityContext.runAsNonRoot: true`. Наш образ из ЛР 1 работает под uid 10001 и проходит проверку:

```
$ kubectl -n notes exec deploy/notes-app -- id
uid=10001(app) gid=10001(app) groups=10001(app)
```

Для сравнения запустили с той же настройкой образ `postgres:16.16-alpine`, который стартует от root:

```
$ kubectl -n notes run rootcheck --image=postgres:16.16-alpine --restart=Never \
    --overrides='{"spec":{"securityContext":{"runAsNonRoot":true}}}'
$ kubectl -n notes get pod rootcheck \
    -o jsonpath='{.status.containerStatuses[0].state.waiting.reason}: {.status.containerStatuses[0].state.waiting.message}'
CreateContainerConfigError: container has runAsNonRoot and image will run as root (pod: "rootcheck_notes(…)", container: rootcheck)
```

Это ещё один довод против пятой плохой практики из ЛР 1.
