# ============================================================================
#  材智通 · 智慧材料库 —— Hugging Face Spaces 根目录 Dockerfile
#
#  HF Docker Spaces 只使用仓库根目录的 Dockerfile，且构建上下文 = 仓库根目录，
#  因此这里从 backend/ 取源码。后端强制的 7860 端口、UID 1000 用户等配置
#  与 backend/Dockerfile 保持一致。
#
#  本地验证：
#    docker build -t smart-materials-server .
#    docker run -p 7860:7860 --env-file backend/.env smart-materials-server
# ============================================================================

# ---------- 阶段 1：Maven 构建 ----------
# 只把后端源码带入构建阶段，前端的 node_modules / dist 不会进入镜像。
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /build

# 先只拷贝 pom，命中 Docker 层缓存，改源码时不必重新下载依赖。
COPY backend/pom.xml ./pom.xml
RUN mvn -B -q -ntp -Dmaven.test.skip=true -DincludeScope=runtime dependency:resolve

COPY backend/src ./src
RUN mvn -B -q -ntp -Dmaven.test.skip=true clean package

# ---------- 阶段 2：运行时（仅 JRE，镜像小） ----------
FROM eclipse-temurin:17-jre-alpine

LABEL org.opencontainers.image.title="smart-materials-server" \
      org.opencontainers.image.description="材智通 · 智慧材料库数字化管理系统 后端" \
      org.opencontainers.image.source="https://github.com/1lxk1/smart-materials"

# HF Spaces 固定端口 7860
ENV PORT=7860 \
    JAVA_OPTS="-XX:MaxRAMPercentage=75 -XX:+UseContainerSupport -Djava.security.egd=file:/dev/./urandom" \
    TZ=Asia/Shanghai \
    UPLOAD_DIR=/app/uploads

# HF Spaces 容器以 UID 1000 运行；显式对齐用户/组 ID，
# 保证 /app/uploads 在该 UID 下可写（否则附件上传会 Permission denied）。
RUN apk add --no-cache tzdata \
    && addgroup -g 1000 -S app \
    && adduser -u 1000 -S app -G app

WORKDIR /app
COPY --from=build /build/target/smart-materials-server.jar /app/app.jar
RUN mkdir -p /app/uploads && chown -R app:app /app

USER app
EXPOSE 7860

HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
    CMD wget -qO- "http://127.0.0.1:7860/api/health" >/dev/null 2>&1 || exit 1

# 7860 为 HF 强制端口，命令行 -D 优先级高于 application.yml 的 ${PORT:8080}
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -Dserver.port=7860 -jar /app/app.jar"]
