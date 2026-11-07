# ============================================================================
#  智慧材料库后端 Dockerfile
#  目标平台：Hugging Face Spaces（Docker SDK）
#  HF Spaces 强制容器监听 7860，因此 EXPOSE 与 -Dserver.port 均固定为 7860。
#  构建：docker build -t smart-materials-server .
#  运行：docker run -p 7860:7860 --env-file .env smart-materials-server
#
#  ⚠️ 注意：render.yaml 里的 smart-materials-api 服务复用了本文件。
#  Render 通过注入 PORT 环境变量分配端口，固定 7860 会让 Render 健康检查失败。
#  若仍需保留 Render 部署，请为 Render 单独维护一份 Dockerfile（入口用
#  -Dserver.port=${PORT:-7860}），或停用 Render 上的该服务。
# ============================================================================

# ---------- 阶段 1：Maven 构建 ----------
# 注意：这里刻意不用 dependency:go-offline —— 它会把所有插件依赖全下一遍，
# 在免费构建机上常因超时/缺件失败。改用 dependency:resolve
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
      org.opencontainers.image.source="https://github.com/1lxk1/smart-materials"

# 端口固定 7860，与 HF Spaces 要求一致
ENV PORT=7860 \
    JAVA_OPTS="-XX:MaxRAMPercentage=75 -XX:+UseContainerSupport -Djava.security.egd=file:/dev/./urandom" \
    TZ=Asia/Shanghai \
    UPLOAD_DIR=/app/uploads

# HF Spaces 容器以 UID 1000 运行；显式对齐用户/组 ID，
# 保证 /app 与 /app/uploads 在该 UID 下可写（否则附件上传会 Permission denied）。
RUN apk add --no-cache tzdata \
    && addgroup -g 1000 -S app \
    && adduser -u 1000 -S app -G app

WORKDIR /app
COPY --from=build /build/target/smart-materials-server.jar /app/app.jar
RUN mkdir -p /app/uploads && chown -R app:app /app

USER app
EXPOSE 7860

# 健康检查直接写死 7860，避免被 --env-file 里的 PORT 覆盖后探测错端口
HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
    CMD wget -qO- "http://127.0.0.1:7860/api/health" >/dev/null 2>&1 || exit 1

# HF Spaces 强制端口 7860：命令行 -Dserver.port 优先级最高，
# 会覆盖 application.yml 中的 ${PORT:8080} 以及任何 PORT 环境变量。
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -Dserver.port=7860 -jar /app/app.jar"]
