# ============================================
# qiproxy-client Dockerfile
# ============================================

FROM node:20-alpine

LABEL maintainer="zhanqi"
LABEL description="qiproxy-client - lightweight intranet penetration proxy client"

WORKDIR /app

# 复制客户端源码
COPY package.json ./
COPY src/ ./src/

# 创建配置目录
RUN mkdir -p /app/conf

# 数据卷：配置文件和 SSL 证书
VOLUME ["/app/conf"]

# 启动命令
CMD ["node", "src/index.js"]
