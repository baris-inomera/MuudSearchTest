# ── Build stage ──────────────────────────────────────────────────────────────
FROM maven:3.9-amazoncorretto-17 AS builder
WORKDIR /app

# Önce sadece pom.xml kopyala → dependency cache'i koru
COPY pom.xml .
RUN mvn dependency:go-offline -q

# Kaynak kodu kopyala ve paketle (testleri atla)
COPY src ./src
RUN mvn package -DskipTests -q

# ── Run stage ─────────────────────────────────────────────────────────────────
FROM amazoncorretto:17
WORKDIR /app

COPY --from=builder /app/target/*.jar app.jar

# cases.json ve web-config.json bu dizinde olmalı (PVC mount edilecek)
VOLUME /app/data

EXPOSE 8090

ENTRYPOINT ["java", "-Dfile.encoding=UTF-8", "-Duser.dir=/app/data", "-jar", "app.jar"]
