# ============================================
# qiproxy-server Dockerfile
# ============================================

# 阶段一：构建
FROM maven:3.9-eclipse-temurin-8-alpine AS builder

WORKDIR /build

# 先复制 pom 文件，利用 Docker 缓存层
COPY pom.xml ./
COPY proxy-common/pom.xml proxy-common/
COPY proxy-protocol/pom.xml proxy-protocol/
COPY proxy-server/pom.xml proxy-server/

# 下载依赖（缓存层）
RUN mvn dependency:go-offline -B

# 复制全部源码
COPY proxy-common/src proxy-common/src
COPY proxy-protocol/src proxy-protocol/src
COPY proxy-server/src proxy-server/src
COPY proxy-server/webpages proxy-server/webpages

# 执行打包（跳过测试）
RUN mvn clean package -DskipTests -B

# 阶段二：运行
FROM eclipse-temurin:8-jre-alpine

LABEL maintainer="zhanqi"
LABEL description="qiproxy-server - lightweight intranet penetration proxy server"

WORKDIR /app

# 从构建阶段复制产物
COPY --from=builder /build/distribution/proxy-server-0.1/ .

# 创建日志目录
RUN mkdir -p /app/logs

# 暴露固定端口
# 4900  - client command channel
# 4993  - SSL client channel
# 8090  - config admin web
EXPOSE 4900 4993 8090

# 暴露动态代理端口范围（可根据需要调整）
EXPOSE 5000-5100

# 数据卷：配置和日志
VOLUME ["/app/conf", "/app/logs"]

# JVM 环境变量
ENV JAVA_OPTS="-Djava.awt.headless=true -Djava.net.preferIPv4Stack=true"
ENV APP_MAINCLASS="org.zhanqi.qiproxy.server.ProxyServerContainer"

# 启动命令
CMD ["sh", "-c", "java $JAVA_OPTS -classpath conf:lib/* $APP_MAINCLASS"]
