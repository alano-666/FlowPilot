# FlowPilot 多阶段构建：前端构建 + 后端打包
FROM node:20-alpine AS frontend
WORKDIR /app/frontend
COPY frontend/package*.json ./
RUN npm install
COPY frontend/ ./
RUN npm run build

FROM maven:3.9-eclipse-temurin-21 AS backend
WORKDIR /app/backend
COPY backend/pom.xml .
RUN mvn -q dependency:go-offline
COPY backend/src ./src
RUN mvn -q package -DskipTests

FROM eclipse-temurin:21-jre
WORKDIR /app
RUN mkdir -p /app/data
COPY --from=backend /app/backend/target/flowpilot.jar app.jar
EXPOSE 8080
VOLUME ["/app/data"]
ENTRYPOINT ["java", "-jar", "app.jar"]
