# Изменения

## ЛР 1 — Dockerfile (метка `lab1`)

- Приложение «Заметки»: Java 21, встроенный HTTP-сервер, заметки в файле `/data/notes.tsv`.
- `Dockerfile` и `Dockerfile.bad`, отчёт [docs/lab1-dockerfile.md](docs/lab1-dockerfile.md).

## ЛР 2 — Docker Compose (метка `lab2`)

- Приложение умеет хранить заметки в PostgreSQL (`STORAGE=postgres`), файловое хранилище осталось для запуска без базы.
- Новая команда образа `migrate`: ждёт базу, создаёт схему и завершается.
- В jar вошёл драйвер PostgreSQL (maven-shade-plugin).
- `docker-compose.yml` и `.env`, отчёт [docs/lab2-compose.md](docs/lab2-compose.md).

## ЛР 3 — Kubernetes (метка `lab3`)

- Манифесты Nextcloud и PostgreSQL из методички в `lab3-nextcloud/`, доработанные по заданию: Secret для Postgres, ConfigMap для Nextcloud, пробы.
- Лимит памяти Nextcloud поднят с 256Mi до 512Mi. С исходным под падал по OOMKilled при входе через браузер.
- Отчёт [docs/lab3-kubernetes.md](docs/lab3-kubernetes.md). Приложение «Заметки» в этой лабе не менялось.

## ЛР 4 — своё приложение в Kubernetes (метка `lab4`)

- Манифесты в `k8s/`: Namespace, ConfigMap и Secret, PostgreSQL с PVC, приложение в двух репликах с init-контейнером `migrate`, Service для обоих.
- Код приложения не менялся: команда `migrate` из ЛР 2 стала init-контейнером без доработок.
- Отчёт [docs/lab4-kubernetes.md](docs/lab4-kubernetes.md).
