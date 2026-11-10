# 后端镜像：多阶段构建（Maven 编译 → JRE 运行）
# 由 deploy/docker-compose.yml 调用：context = backend/，dockerfile = Dockerfile

# ---- 构建阶段 ----
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /workspace
# 先只拷 pom，改代码时不必重新下载依赖（依赖变了才失效）
COPY pom.xml ./
RUN mvn -B -ntp -q -DskipTests dependency:go-offline
COPY src ./src
RUN mvn -B -ntp -DskipTests package

# ---- 运行阶段 ----
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
# 不用 root 跑应用
RUN addgroup -S app && adduser -S app -G app
COPY --from=build /workspace/target/medical-platform-backend.jar ./app.jar
USER app
EXPOSE 8080
# 容器内存交给 JVM 感知 cgroup，避免写死 -Xmx
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "/app/app.jar"]
