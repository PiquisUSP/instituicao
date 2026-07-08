# syntax=docker/dockerfile:1

# ---------- Estágio de build ----------
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build

# 1) Copia só o pom e baixa as dependências (camada em cache enquanto o pom não muda)
COPY pom.xml .
RUN mvn -q -B dependency:go-offline

# 2) Compila e empacota o fat jar do Spring Boot (sem rodar testes)
COPY src ./src
RUN mvn -q -B -DskipTests clean package

# ---------- Estágio de runtime ----------
FROM amazoncorretto:21 AS runtime
WORKDIR /app

COPY --from=build /build/target/instituicao-1.0-SNAPSHOT.jar app.jar

# Log + snapshots do Raft. Monte um volume aqui para persistir entre restarts.
VOLUME ["/app/raft-storage"]

# HTTP (REST) e Raft/gRPC
EXPOSE 8080 7001 7002 7003

# Config via args/env do Spring. Ex.: docker run <img> --instituicao.node-id=n2 --server.port=8080
# 'exec' faz o Java receber o SIGTERM (shutdown limpo do nó Raft).
# $JAVA_OPTS permite passar flags de JVM.
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar \"$@\"", "--"]
CMD ["--instituicao.node-id=n1", "--server.port=8080"]
