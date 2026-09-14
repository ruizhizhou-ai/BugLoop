# 本文件将 Spring Boot 后端构建为精简运行镜像，构建阶段和运行阶段相互隔离。
FROM maven:3.9-eclipse-temurin-21 AS builder
WORKDIR /workspace
COPY backend/pom.xml backend/pom.xml
RUN mvn -f backend/pom.xml dependency:go-offline
COPY backend backend
RUN mvn -f backend/pom.xml clean package -DskipTests

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=builder /workspace/backend/target/bugloop-*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]

