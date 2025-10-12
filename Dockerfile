### web前端构建阶段 ###
FROM node:22 AS web-build-stage
WORKDIR /app

# 定义版本参数，默认为latest
ARG TAG_VERSION=latest

COPY web/ .

RUN corepack enable && \
    yarn && \
    yarn build

### 后端构建阶段 ###
FROM ibm-semeru-runtimes:open-24-jdk-noble AS backend-build-stage
WORKDIR /app

# 定义版本参数，默认为latest
ARG TAG_VERSION=latest

COPY server/ .

# 从前端构建阶段复制构建结果到后端静态资源目录
COPY --from=web-build-stage /app/dist /app/src/main/resources/static

# 构建项目
RUN ./gradlew clean shadowJar --no-daemon

### 生产阶段 ###
FROM ibm-semeru-runtimes:open-24-jre-noble
WORKDIR /app

# 安装 ExifTool
RUN apt-get update && \
    apt-get install -y --no-install-recommends ca-certificates libimage-exiftool-perl && \
    rm -rf /var/lib/apt/lists/*

# 从构建阶段复制构建结果
COPY --from=backend-build-stage /app/build/libs/*.jar app.jar

# 暴露应用端口(默认Spring Boot端口)
EXPOSE 8080

# 允许通过环境变量传递JVM参数
# 隐藏 SQLite 的警告信息
ENV JAVA_OPTS="--enable-native-access=ALL-UNNAMED"

# 启动应用
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]