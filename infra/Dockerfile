# syntax=docker/dockerfile:1

# ---------- Etapa de construcción ----------
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build

# Las dependencias se resuelven en una capa aparte para que un cambio de código
# no invalide la caché de descarga.
COPY pom.xml ./
COPY backend/shared-kernel/pom.xml backend/shared-kernel/
COPY backend/iam/pom.xml backend/iam/
COPY backend/accounts/pom.xml backend/accounts/
COPY backend/expenses/pom.xml backend/expenses/
COPY backend/mortgage/pom.xml backend/mortgage/
COPY backend/bootstrap/pom.xml backend/bootstrap/
RUN mvn -B -q dependency:go-offline

COPY backend backend
RUN mvn -B -q clean package

# ---------- Etapa de ejecución ----------
FROM eclipse-temurin:21-jre-alpine AS runtime

# Privilegio mínimo: el proceso nunca corre como root (CCN-STIC-812).
RUN addgroup -S gastos && adduser -S gastos -G gastos
WORKDIR /app

COPY --from=build --chown=gastos:gastos /build/backend/bootstrap/target/gastos.jar app.jar

USER gastos
EXPOSE 8080

# El heap se ajusta al contenedor: imprescindible en instancias de 256-512 MB.
ENV JAVA_OPTS="-XX:MaxRAMPercentage=70 -XX:+UseSerialGC -XX:TieredStopAtLevel=1"

HEALTHCHECK --interval=30s --timeout=3s --start-period=40s --retries=3 \
  CMD wget -qO- http://127.0.0.1:8080/actuator/health || exit 1

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]
