# syntax=docker/dockerfile:1

# Las imagenes base van fijadas por DIGEST y no solo por etiqueta. Una etiqueta se puede
# reapuntar a otro contenido sin avisar; un digest no. Dependabot abre PR cuando salen
# versiones nuevas, asi que fijarlo no significa quedarse atras.

# ---------- Etapa de construcción ----------
FROM maven:3-eclipse-temurin-26@sha256:c30718f442eb39c55b2d45440bb849d8755ad8d9d6ef67aadefcce150267116f AS build
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
FROM eclipse-temurin:21-jre-alpine@sha256:1a29e1fe337eb28b5bec30f0ee8ed29f0ff80ab6f75dcf9313efe82911065a52 AS runtime

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
