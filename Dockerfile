# Хороший Dockerfile. Разбор: docs/lab1-dockerfile.md

# --- Этап сборки: Maven и JDK нужны только здесь ---
FROM maven:3.9.16-eclipse-temurin-21-alpine AS build
WORKDIR /build

# Сначала только pom.xml: слой с зависимостями и плагинами Maven
# пересобирается, лишь когда меняется pom.xml, а не любой файл с кодом.
COPY app/pom.xml .
RUN mvn -B -q dependency:go-offline

COPY app/src ./src
RUN mvn -B -q package

# --- Этап запуска: только JRE и готовый jar ---
FROM eclipse-temurin:21.0.13_9-jre-alpine

# Отдельный пользователь без прав root. UID числовой, чтобы Kubernetes
# мог проверить runAsNonRoot.
RUN addgroup -S -g 10001 app \
 && adduser -S -D -H -u 10001 -G app app \
 && mkdir /data \
 && chown app:app /data

WORKDIR /app
COPY --from=build /build/target/notes.jar app.jar

USER 10001:10001
ENV DATA_DIR=/data
EXPOSE 8080

HEALTHCHECK --interval=10s --timeout=3s --start-period=15s --retries=3 \
    CMD wget -q -O /dev/null http://127.0.0.1:8080/health/live || exit 1

# exec-форма: java получает PID 1 и сам принимает SIGTERM.
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "/app/app.jar"]
CMD ["serve"]
