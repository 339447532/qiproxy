# qiproxy-client

qiproxy 的 Node.js 客户端，用于将内网服务通过代理服务器暴露给外部访问。

## 环境要求

- Node.js >= 12.0.0（脚本会自动检测并安装）

## 快速开始

### 一键启动（推荐）

自动检测 Node.js 环境，直接启动：

**Linux / macOS:**
```bash
cd proxy-client
./start.sh
```

**Windows:**
```cmd
cd proxy-client
start.bat
```

### 手动启动

#### 1. 配置

编辑 `conf/config.properties`:

```properties
# 客户端认证密钥（必填）
client.key=your-client-key

# SSL配置
ssl.enable=false
ssl.certPath=client-cert.pem
ssl.keyPath=client-key.pem
ssl.keyPassword=changeit

# 代理服务器地址
server.host=39.108.124.205
server.port=4900

# 日志级别: DEBUG, INFO, WARN, ERROR
log.level=INFO
```

#### 2. 启动

```bash
node src/index.js
```

## 配置说明

| 配置项 | 说明 | 必填 |
|--------|------|------|
| `client.key` | 客户端认证密钥，用于服务器验证客户端身份 | 是 |
| `ssl.enable` | 是否启用 SSL 加密连接 | 否 |
| `ssl.certPath` | SSL 证书路径（PEM 格式） | SSL 启用时必填 |
| `ssl.keyPath` | SSL 私钥路径（PEM 格式） | SSL 启用时必填 |
| `ssl.keyPassword` | SSL 私钥密码 | SSL 启用时必填 |
| `server.host` | 代理服务器地址 | 是 |
| `server.port` | 代理服务器端口，默认 4900 | 否 |
| `log.level` | 日志级别：DEBUG, INFO, WARN, ERROR | 否，默认 INFO |

## 工作原理

### 连接流程

1. 客户端使用 `client.key` 连接到代理服务器（端口 4900）
2. 服务器验证通过后，客户端等待服务器下发连接指令
3. 当有用户请求访问时，服务器发送 `TYPE_CONNECT` 指令
4. 客户端连接到内网目标服务（如 `192.168.1.99:80`）
5. 建立双向通道，数据在用户和内网服务之间转发

### 消息类型

| 类型 | 值 | 说明 |
|------|-----|------|
| TYPE_HEARTBEAT | 0x07 | 心跳保活 |
| C_TYPE_AUTH | 0x01 | 认证消息 |
| TYPE_CONNECT | 0x03 | 连接内网服务 |
| TYPE_DISCONNECT | 0x04 | 断开连接 |
| P_TYPE_TRANSFER | 0x05 | 数据传输 |
| C_TYPE_WRITE_CONTROL | 0x06 | 写控制 |

### 通道管理

- **命令通道**：第一个建立的连接，用于认证和接收服务器指令
- **数据通道**：从连接池获取，用于转发实际业务数据
- **连接池**：最多缓存 100 个数据通道，复用减少建连开销

### 重连策略

当命令通道断开时，客户端自动重连，采用指数退避策略：

| 参数 | 值 |
|------|-----|
| 初始等待时间 | 1 秒 |
| 最大等待时间 | 60 秒 |
| 退避倍数 | 2 倍 |

**退避序列**：1s → 2s → 4s → 8s → 16s → 32s → 60s → 60s → ...

重连成功后，等待时间重置为 1 秒。数据通道断开不会触发重连。

## 项目结构

```
proxy-client/
├── conf/
│   ├── config.properties    # 配置文件
│   ├── client-cert.pem      # SSL 客户端证书（PEM格式）
│   ├── client-key.pem       # SSL 客户端私钥（PEM格式）
│   └── ca-cert.pem         # CA 根证书
├── src/
│   ├── index.js             # 入口，ProxyClientContainer
│   ├── channelManager.js    # 通道管理器
│   ├── config.js            # 配置读取
│   ├── logger.js            # 日志管理
│   ├── constants.js         # 常量定义
│   ├── protocol.js           # 协议编解码
│   └── sslContext.js         # SSL上下文
└── package.json
```

## 信号处理

客户端支持优雅关闭：

- `SIGINT` (Ctrl+C)：正常关闭，销毁所有连接后退出
- `SIGTERM`：正常关闭，销毁所有连接后退出

## 调试

日志输出到标准输出，包含以下级别：

- `[INFO]`：正常运行信息
- `[WARN]`：警告信息（如连接断开）
- `[ERROR]`：错误信息
- `[DEBUG]`：调试信息（消息类型）

## SSL 证书

生产级 SSL 证书位于 `conf/` 目录：

| 文件 | 说明 |
|------|------|
| `client-cert.pem` | SSL 客户端证书（PEM 格式） |
| `client-key.pem` | SSL 客户端私钥（PEM 格式） |
| `ca-cert.pem` | CA 根证书（用于验证服务器证书） |

**证书信息**：
- 算法：RSA 2048-bit
- 签名算法：SHA256withRSA
- 有效期：10 年（2026-2036）
- CN：qiproxy-client
