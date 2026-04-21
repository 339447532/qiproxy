# qiproxy

qiproxy 是一款轻量级高性能的内网穿透代理服务，支持 TCP/UDP 协议，无缝穿透 NAT 和防火墙。

## 项目结构

```
qiproxy/
├── proxy-common/           # 公共模块：配置、工具类、容器接口
├── proxy-protocol/        # 协议模块：消息编解码、心跳检测
├── proxy-server/          # Java 服务器端（源码）
├── proxy-client/          # Node.js 客户端
├── distribution/           # 预编译打包目录
│   └── proxy-server-0.1/
│       ├── bin/           # 启动脚本
│       ├── conf/         # 配置文件
│       └── webpages/      # 管理后台前端
└── ssl/                   # SSL 证书（生产级 RSA 2048-bit）
```

## 快速开始

### 启动服务端

```bash
cd distribution/proxy-server-0.1
./bin/startup.sh    # Linux/macOS
./bin/startup.bat   # Windows
```

### 启动客户端

```bash
cd proxy-client
./start.sh          # Linux/macOS
start.bat           # Windows
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
- **管理后台**：Layui + jQuery + JSON Editor

## 构建

```bash
# 构建服务端
cd proxy-server
mvn clean package

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

