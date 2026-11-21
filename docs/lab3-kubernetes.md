# ЛР 3. Kubernetes

Развернули в minikube Nextcloud с PostgreSQL по манифестам из методички и доработали их: логин и пароль Postgres перенесли в Secret, переменные Nextcloud в ConfigMap, добавили Nextcloud пробы. Манифесты лежат в [lab3-nextcloud/](../lab3-nextcloud).

## Запуск

minikube v1.39.0, Kubernetes v1.37.0, драйвер docker. Docker Hub из нашей сети недоступен, поэтому кластер запущен с зеркалом.

```bash
minikube start --driver=docker --cpus=4 --memory=4096 --registry-mirror=https://mirror.gcr.io
kubectl apply -f lab3-nextcloud/
kubectl expose deployment nextcloud --type=NodePort --port=80
minikube service nextcloud
```

Установка занимает 2–3 минуты, после неё можно войти как `labadmin` с паролем из `nextcloud-secret`.

```
$ kubectl get deploy,pods,svc          # вывод сокращён
pod/nextcloud-676d5cd899-wmc2d   1/1     Running   0          117s
pod/postgres-848ffcb678-9tq7d    1/1     Running   0          2m43s
service/nextcloud          NodePort    10.99.234.251    <none>        80:30725/TCP     152m
```

| Страница входа | После входа |
|---|---|
| ![Вход в Nextcloud](img/lab3-nextcloud-login.png) | ![Nextcloud после входа](img/lab3-nextcloud-files.png) |

Dashboard (`minikube dashboard`):

![minikube dashboard](img/lab3-dashboard.png)

## Пункты задания

| Пункт | Где сделано |
|---|---|
| Postgres: `POSTGRES_USER` и `POSTGRES_PASSWORD` в Secret | [pg_secret.yml](../lab3-nextcloud/pg_secret.yml), подключён через `envFrom` в [pg_deployment.yml](../lab3-nextcloud/pg_deployment.yml). В [pg_configmap.yml](../lab3-nextcloud/pg_configmap.yml) осталась только `POSTGRES_DB` |
| Nextcloud: переменные из Deployment в ConfigMap | `nextcloud-configmap` в [nextcloud.yml](../lab3-nextcloud/nextcloud.yml), подключён через `envFrom` |
| Nextcloud: liveness и readiness | там же, плюс startup-проба |
| Ход работы со скриншотами, ответы на вопросы | этот отчёт |

Значения Postgres Nextcloud берёт по ключу (`configMapKeyRef`, `secretKeyRef`), лишние в его окружение не попадают. Secret значения не шифрует: `kubectl get secret -o yaml` отдаёт их в base64. Он позволяет ограничить доступ через RBAC и включить шифрование в etcd. Поэтому учебные пароли лежат в репозитории открытым текстом, а в рабочем проекте Secret создают из хранилища секретов.

`ALLOW_EMPTY_PASSWORD` и `NEXTCLOUD_UPDATE` из методички на этот образ не влияют (видно по `/entrypoint.sh`), но мы их оставили.

## Пробы

| Проба | Что проверяет | Зачем |
|---|---|---|
| startup | `GET /status.php`, до 10 минут | Пока идёт установка, остальные пробы не запускаются и не убьют под |
| readiness | `GET /status.php` | Отвечают ли PHP, конфиг и база. Если нет, трафик на под не идёт |
| liveness | `GET /robots.txt` | Жив ли Apache. Статический файл не зависит от базы, и падение Postgres не перезапускает Nextcloud |

Пробам на `/status.php` нужен заголовок `Host: 127.0.0.1`: kubelet обращается по IP пода, а Nextcloud отвечает только доверенным доменам.

## Ответы на вопросы

### Важен ли порядок применения манифестов?

На итог не влияет, на ход запуска влияет. Мы применили `nextcloud.yml` раньше объектов Postgres:

```
$ kubectl get pods
NAME                         READY   STATUS                       RESTARTS   AGE
nextcloud-75cc5d494c-8l9jk   0/1     CreateContainerConfigError   0          20s
$ kubectl describe pod -l app=nextcloud
  Warning  Failed  8s (x3 over 20s)  kubelet  Error: configmap "postgres-configmap" not found
```

Kubelet повторял попытку. Когда появились объекты Postgres, контейнер запустился в том же поде. Kubernetes декларативен: контроллеры доводят кластер до описанного состояния, в каком бы порядке ни появлялись объекты. Поэтому работает и `kubectl apply -f lab3-nextcloud/`, хотя файлы применяются по алфавиту и `nextcloud.yml` идёт первым.

Порядок всё же важен в трёх случаях:

- пока зависимостей нет, под не работает и в событиях копятся ошибки;
- приложение может стартовать раньше базы. Образ Nextcloud сам повторяет установку до 10 раз, а своему приложению для этого нужен init-контейнер (ЛР 4);
- объект нельзя создать в несуществующем Namespace, поэтому Namespace применяют первым.

Удобный порядок: Namespace, ConfigMap и Secret, Service, Deployment. Service ставим раньше Deployment, потому что переменные `*_SERVICE_HOST` под получает только от Service, которые уже были при его запуске. Nextcloud ищет базу по DNS-имени, и ему этот порядок не важен.

### Что будет, если уменьшить число реплик Postgres до 0, потом вернуть 1 и зайти в Nextcloud?

Nextcloud сломается и сам не восстановится. Проверили, запрашивая `GET /login` через Service после каждого шага:

| Момент | Таблиц Nextcloud в базе | `GET /login` |
|---|---|---|
| До | 131 | 200 |
| `replicas=0` | база недоступна | 500 |
| Снова `replicas=1` | 0 | нет ответа |

У Postgres нет тома, и новый под поднял пустую базу. Nextcloud считает себя установленным (`'installed' => true` в `config.php`) и подключается пользователем `oc_admin`, которого в новой базе нет:

```
FATAL: password authentication failed for user "oc_admin"
```

Readiness убирает под из Service, liveness проходит, поэтому под не перезапускается. Вернуть Nextcloud удалось только переустановкой (`kubectl rollout restart deployment nextcloud`), все данные потеряны. Базе нужен PersistentVolumeClaim, в ЛР 4 он есть.

## Что пошло не по методичке

Лимита памяти 256Mi Nextcloud не хватило: при первом входе через браузер под падал с `OOMKilled`. С 512Mi вход проходит. Процессам после входа нужно около 121 МиБ, остальное до лимита занимает файловый кэш, который ядро вытесняет при нехватке памяти.

Без тома Nextcloud переустанавливается в каждом новом контейнере. Rolling update при смене лимита упал с `Installing of nextcloud failed!`, пришлось удалить оба Deployment и применить манифесты заново.
