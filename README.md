# qiproxy

qiproxy 是一款轻量级高性能的内网穿透代理服务，支持 TCP/UDP 协议，无缝穿透 NAT 和防火墙。

## 项目结构

```
qiproxy/
├── proxy-common/           # 公共模块：配置、工具类、容器接口
├── proxy-protocol/        # 协议模块：消息编解码、心跳检测
├── proxy-server/          # Java 服务器端（源码）
├── proxy-client/          # Node.js 客户端
├── android/               # Android 客户端 App（原生 Java）
│   ├── app/src/main/java/  # 源码：协议、网络、核心、Service、UI
│   └── app/src/main/res/   # 布局、配置、资源
├── distribution/           # 预编译打包目录
│   └── proxy-server-0.1/
│       ├── bin/           # 启动脚本
│       ├── conf/         # 配置文件
│       └── webpages/      # 管理后台前端
└── ssl/                   # SSL 证书（生产级 RSA 2048-bit）
```

## 快速开始

### Docker 一键部署（推荐）

```bash
# 构建镜像
./deploy.sh build

# 启动服务端
./deploy.sh start

# 启动客户端（交互模式）
./deploy.sh client
```

支持命令：`build`、`start`、`stop`、`restart`、`status`、`logs`、`client`

### Android 客户端

#### 功能特性

- 原生 Java 实现，完整复刻 Node.js 客户端功能
- 自定义二进制协议（长度前缀帧 + 6 种消息类型）
- SSL/TLS 加密连接（内置 PEM 证书和私钥）
- 数据通道池化复用（最大 100 个连接）
- 指数退避断线重连（1s → 2s → 4s ... 封顶 60s）
- 前台服务保活（`dataSync` 类型 + START_STICKY）
- HTTP 请求嗅探（实时显示数据通道请求/响应摘要）
- 侧边栏菜单导航（设置 + GitHub 快捷入口）
- 实时日志显示（批量刷新 + 200 条上限防内存泄漏）

#### 构建

```bash
cd android

# Debug 包
./gradlew assembleDebug

# Release 包（使用生产签名）
./gradlew assembleRelease
```

Release APK 输出路径：`android/app/build/outputs/apk/release/app-release.apk`

#### 安装

```bash
# Debug
adb install -r app/build/outputs/apk/debug/app-debug.apk

# Release
adb install -r app/build/outputs/apk/release/app-release.apk
```

#### 使用

1. 打开 App，侧滑菜单 → **设置**，确认 Server Host、Port、Client Key
2. 返回主界面，点击 **启动** 启动前台服务
3. 服务会在通知栏常驻，保持后台连接
4. 日志区域实时显示连接状态、数据通道请求和响应

#### Android 客户端技术栈

| 层级 | 技术 |
|------|------|
| 网络层 | 原生 `java.net.Socket` / `SSLSocket` + 阻塞 I/O |
| 协议层 | `java.nio.ByteBuffer`（显式 `BIG_ENDIAN`）+ `FrameDecoder` |
| 线程模型 | 每连接独立线程 + `ScheduledExecutorService`（心跳/idle） |
| 配置存储 | `EncryptedSharedPreferences`（AndroidX Security） |
| PEM 解析 | BouncyCastle `bcpkix-jdk15on` |
| UI | AppCompat + Material Design + RecyclerView |
| 服务 | `ForegroundService`（`dataSync`）+ `BOOT_COMPLETED` 自启动 |

### 手动启动服务端

```bash
cd distribution/proxy-server-0.1
./bin/startup.sh    # Linux/macOS
./bin/startup.bat   # Windows
```

### 手动启动客户端

```bash
cd proxy-client
./start.sh          # Linux/macOS
start.bat           # Windows
```

## Docker 部署

项目已提供完整的 Docker 支持，可通过 `docker-compose.yml` 一键编排。

### 端口映射

| 端口 | 用途 |
|------|------|
| 4900 | 代理服务端口（客户端连接） |
| 4993 | SSL 代理端口（加密连接） |
| 8090 | 管理后台（Web UI） |
| 5000-5100 | 动态代理端口范围（可在 `docker-compose.yml` 中调整） |

### 目录挂载

| 容器路径 | 宿主机路径 | 说明 |
|----------|-----------|------|
| `/app/conf` | `./proxy-server/conf` | 服务端配置和 SSL 证书 |
| `/app/logs` | `./proxy-server/logs` | 服务端日志 |
| `/app/conf` | `./proxy-client/conf` | 客户端配置和 SSL 证书 |

### 常用命令

```bash
# 构建镜像
./deploy.sh build

# 启动服务端
./deploy.sh start

# 查看运行状态
./deploy.sh status

# 查看服务端日志
./deploy.sh logs

# 启动客户端（交互模式，便于调试）
./deploy.sh client

# 重启服务
./deploy.sh restart

# 停止服务
./deploy.sh stop
```

## 服务架构

### 端口说明

| 端口 | 用途 |
|------|------|
| 4900 | 代理服务端口（客户端连接） |
| 4993 | SSL 代理端口（加密连接） |
| 8090 | 管理后台（Web UI） |

### 工作原理

```
用户 ──→ Server:port ──→ Client ──→ 内网服务
         ↑                  ↑
      用户端口            client.key 认证
```

1. 客户端使用 `client.key` 连接服务器（端口 4900）
2. 服务器验证通过后，等待用户请求
3. 用户连接服务器的用户端口
4. 服务器通知客户端建立到内网服务的连接
5. 数据在用户和内网服务之间双向转发

## 配置说明

### 服务端配置

文件：`distribution/proxy-server-0.1/conf/config.properties`

```properties
server.bind=0.0.0.0
server.port=4900

server.ssl.enable=true
server.ssl.bind=0.0.0.0
server.ssl.port=4993
server.ssl.jksPath=server.jks
server.ssl.keyStorePassword=changeit
server.ssl.keyManagerPassword=changeit

config.server.bind=0.0.0.0
config.server.port=8090
config.admin.username=admin
config.admin.password=admin
```

### 客户端配置

文件：`proxy-client/conf/config.properties`

```properties
client.key=your-client-key

ssl.enable=false
ssl.certPath=client-cert.pem
ssl.keyPath=client-key.pem
ssl.keyPassword=changeit

server.host=your-server-ip
server.port=4900
```

### Android 客户端默认配置

| 配置项 | 默认值 |
|--------|--------|
| Server Host | `39.108.124.205` |
| Server Port | `4993` |
| Client Key | `a513deab23ee47698934f7f8507b1ca5` |
| SSL Enable | `true` |
| Log Level | `INFO` |

> 证书、私钥和密钥密码已内置到 APK 中，无需手动配置。

### 管理后台

访问 `http://server:8090`，使用管理员账号登录后管理客户端和端口映射。

## SSL 证书

项目已生成生产级 SSL 证书（RSA 2048-bit, SHA256withRSA, 10年有效期）：

| 用途 | 文件 |
|------|------|
| 服务端 | `distribution/proxy-server-0.1/conf/server.jks` |
| 客户端 | `proxy-client/conf/client-cert.pem`, `client-key.pem`, `ca-cert.pem` |
| 原始证书 | `ssl/` 目录 |

默认密钥库密码：`changeit`

## 技术栈

- **服务端**：Java 8 + Netty 4.0.36 + SLF4J + Log4j
- **客户端**：Node.js (>=12.0.0)
- **Android 客户端**：Java + AndroidX + SSLSocket + BouncyCastle
- **管理后台**：Layui + jQuery + JSON Editor

## 构建

### Docker 构建（推荐）

```bash
# 构建服务端和客户端镜像
./deploy.sh build
```

- `proxy-server/Dockerfile`：多阶段构建，基于 `maven:3.9-eclipse-temurin-8` 编译，`eclipse-temurin:8-jre-alpine` 运行
- `proxy-client/Dockerfile`：基于 `node:20-alpine`

### 手动构建

```bash
# 构建服务端
cd proxy-server
mvn clean package

# Android 客户端
cd android
./gradlew assembleRelease

# 客户端无需构建，直接运行
```

## 协议消息类型

| 类型 | 值 | 说明 |
|------|-----|------|
| TYPE_HEARTBEAT | 0x07 | 心跳保活 |
| C_TYPE_AUTH | 0x01 | 客户端认证 |
| TYPE_CONNECT | 0x03 | 连接内网服务 |
| TYPE_DISCONNECT | 0x04 | 断开连接 |
| P_TYPE_TRANSFER | 0x05 | 数据传输 |
| C_TYPE_WRITE_CONTROL | 0x06 | 写控制 |
