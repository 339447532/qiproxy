# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build Commands

### Java Server (Maven multi-module)
```bash
mvn clean package        # Build all modules
mvn compile              # Compile only
mvn test                 # Run tests
```

### Node.js Client
```bash
cd proxy-client
node src/index.js        # Run directly (no build needed)
```

## Architecture

### Modules
- **proxy-common**: Shared utilities (Config, JsonUtil, Container interface)
- **proxy-protocol**: Network protocol (ProxyMessage, ProxyMessageEncoder/Decoder, IdleCheckHandler)
- **proxy-server**: Netty-based Java server handling client connections and user requests
- **proxy-client**: Node.js client (pure JavaScript, no build step)

### Server Architecture
The proxy server (`ProxyServerContainer`) runs three independent Netty server groups:
1. **Client command channel** (port 4900): Handles client authentication and control messages
2. **SSL client channel** (port 4993): TLS-encrypted client connections
3. **User access channels**: Dynamically allocated ports that forward to backend services

### Protocol Flow
1. Client authenticates with server using `clientKey`
2. Client establishes connections for each proxy mapping (e.g., `192.168.1.99:80`)
3. Users connect to server's user ports → server forwards to appropriate client → client connects to internal service

### Key Classes
- `ProxyMessage`: Protocol message with types (HEARTBEAT, AUTH, CONNECT, DISCONNECT, TRANSFER, WRITE_CONTROL)
- `ProxyChannelManager`: Manages channel mappings between clients and users
- `ServerChannelHandler`: Handles messages from proxy clients
- `UserChannelHandler`: Handles messages from end users accessing proxied services
- `ProxyConfig`: Server configuration stored in `~/.qiproxy/config.json`

## Configuration Files

| Component | Location |
|-----------|----------|
| Server static config | `distribution/proxy-server-0.1/conf/config.properties` |
| Server runtime data | `~/.qiproxy/config.json` |
| Client config | `proxy-client/conf/config.properties` |
| Server SSL keystore | `distribution/proxy-server-0.1/conf/server.jks` |
| Client SSL certificates | `proxy-client/conf/*.pem` |

## SSL Certificates

Production-grade SSL certificates generated (RSA 2048-bit, SHA256withRSA, valid until 2036):

| Component | File |
|-----------|------|
| Server | `server.jks` (JKS format) |
| Client | `client-cert.pem`, `client-key.pem`, `ca-cert.pem` (PEM format) |

Default keystore password: `changeit`
