# ============================================================================
#  智慧材料库后端 Dockerfile
#  运行镜像：eclipse-temurin:17-jre-alpine（Render 直接用它构建）
#  构建：docker build -t smart-materials-server .
#  运行：docker run -p 8080:8080 --env-file .env smart-materials-server
# ============================================================================

# ---------- 阶段 1：Maven 构建 ----------
# 注意：这里刻意不用 dependency:go-offline —— 它会把所有插件依赖全下一遍，
# 在 Render 免费构建机上常因超时/缺件失败。改用 dependency:resolve
# （只下项目依赖，速度快很多），剩余插件在 package 阶段按需下载。
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /build

# 先拷贝 pom，利用 Docker 层缓存加速二次构建。
# -Dmaven.test.skip=true：镜像里不需要测试，连测试源码都不编译，
#   同时避免下载内嵌 PostgreSQL 等仅测试用的二进制（每个约 15MB）。
# -DincludeScope=runtime：只预取编译+运行期依赖。
COPY pom.xml ./
RUN mvn -B -q -ntp -Dmaven.test.skip=true -DincludeScope=runtime dependency:resolve

COPY src ./src
RUN mvn -B -q -ntp -Dmaven.test.skip=true clean package

# ---------- 阶段 2：运行时（仅 JRE，镜像小） ----------
FROM eclipse-temurin:17-jre-alpine

LABEL org.opencontainers.image.title="smart-materials-server" \
      org.opencontainers.image.description="材智通 · 智慧材料库数字化管理系统 后端" \
      org.opencontainers.image.source="https://github.com/your-org/smart-materials"

# Render / 容器平台通过 PORT 注入监听端口
ENV PORT=8080 \
    JAVA_OPTS="-XX:MaxRAMPercentage=75 -XX:+UseContainerSupport -Djava.security.egd=file:/dev/./urandom" \
    TZ=Asia/Shanghai \
    UPLOAD_DIR=/app/uploads

RUN apk add --no-cache tzdata \
    && addgroup -S app && adduser -S app -G app

WORKDIR /app
COPY --from=build /build/target/smart-materials-server.jar /app/app.jar
RUN mkdir -p /app/uploads && chown -R app:app /app

USER app
EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
    CMD wget -qO- "http://127.0.0.1:${PORT}/api/health" >/dev/null 2>&1 || exit 1

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
