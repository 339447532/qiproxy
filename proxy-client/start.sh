#!/bin/bash

# ============================================
# qiproxy-client 启动脚本 (Linux/macOS)
# ============================================

set -e

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
cd "$SCRIPT_DIR"

# 颜色定义
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

log_info() {
    echo -e "${GREEN}[INFO]${NC} $1"
}

log_warn() {
    echo -e "${YELLOW}[WARN]${NC} $1"
}

log_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

# 检查 Node.js 是否安装
check_node() {
    if command -v node &> /dev/null; then
        NODE_VERSION=$(node --version)
        log_info "Node.js 已安装: $NODE_VERSION"
        return 0
    else
        return 1
    fi
}

# 安装 Node.js (Linux)
install_node_linux() {
    log_warn "正在安装 Node.js (Linux)..."

    # 检测架构
    ARCH=$(uname -m)
    if [ "$ARCH" = "x86_64" ]; then
        NODE_URL="https://npmmirror.com/mirrors/node/v20.10.0/node-v20.10.0-linux-x64.tar.xz"
    elif [ "$ARCH" = "aarch64" ] || [ "$ARCH" = "arm64" ]; then
        NODE_URL="https://npmmirror.com/mirrors/node/v20.10.0/node-v20.10.0-linux-arm64.tar.xz"
    else
        log_error "不支持的架构: $ARCH"
        exit 1
    fi

    TMP_DIR="/tmp/qiproxy-node-install"
    mkdir -p "$TMP_DIR"

    log_info "下载 Node.js..."
    curl -fsSL "$NODE_URL" -o "$TMP_DIR/node.tar.xz"

    log_info "安装 Node.js..."
    sudo tar -xf "$TMP_DIR/node.tar.xz" -C /usr/local --strip-components=1

    rm -rf "$TMP_DIR"

    log_info "Node.js 安装完成: $(node --version)"
}

# 安装 Node.js (macOS)
install_node_macos() {
    log_warn "正在安装 Node.js (macOS)..."

    if command -v brew &> /dev/null; then
        log_info "使用 Homebrew 安装..."
        brew install node@20
    else
        log_error "未检测到 Homebrew，请先安装: https://brew.sh"
        exit 1
    fi

    log_info "Node.js 安装完成: $(node --version)"
}

# 启动客户端
start_client() {
    log_info "启动 qiproxy-client..."
    node src/index.js
}

# 主流程
main() {
    echo "========================================"
    echo "  qiproxy-client 启动脚本"
    echo "========================================"
    echo ""

    # 检查 Node.js
    if ! check_node; then
        log_warn "未检测到 Node.js，开始自动安装..."

        OS=$(uname -s)
        case "$OS" in
            Linux*)
                install_node_linux
                ;;
            Darwin*)
                install_node_macos
                ;;
            *)
                log_error "不支持的操作系统: $OS"
                exit 1
                ;;
        esac
    fi

    # 启动
    start_client
}

main
