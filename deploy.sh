#!/bin/bash

# ============================================
# qiproxy 一键部署脚本
# 支持: build | start | stop | restart | status | logs | client
# ============================================

set -e

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
cd "$SCRIPT_DIR"

# 颜色定义
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

log_info() {
    echo -e "${GREEN}[INFO]${NC} $1"
}

log_warn() {
    echo -e "${YELLOW}[WARN]${NC} $1"
}

log_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

log_step() {
    echo -e "${BLUE}[STEP]${NC} $1"
}

# 检查 Docker
check_docker() {
    if ! command -v docker &> /dev/null; then
        log_error "Docker 未安装，请先安装 Docker: https://docs.docker.com/get-docker/"
        exit 1
    fi

    if ! command -v docker-compose &> /dev/null && ! docker compose version &> /dev/null; then
        log_error "Docker Compose 未安装，请先安装 Docker Compose: https://docs.docker.com/compose/install/"
        exit 1
    fi

    log_info "Docker 环境检测通过"
}

# 获取 docker compose 命令
docker_compose_cmd() {
    if docker compose version &> /dev/null; then
        echo "docker compose"
    else
        echo "docker-compose"
    fi
}

# 构建镜像
cmd_build() {
    log_step "开始构建 qiproxy 镜像..."
    check_docker

    local COMPOSE=$(docker_compose_cmd)

    log_info "构建服务端镜像 (qiproxy-server)..."
    $COMPOSE build proxy-server

    log_info "构建客户端镜像 (qiproxy-client)..."
    $COMPOSE --profile client build proxy-client

    log_info "构建完成！"
}

# 启动服务
cmd_start() {
    log_step "启动 qiproxy-server..."
    check_docker

    local COMPOSE=$(docker_compose_cmd)

    # 确保日志目录存在
    mkdir -p proxy-server/logs
    mkdir -p proxy-client/conf

    $COMPOSE up -d proxy-server

    log_info "qiproxy-server 已启动"
    log_info "管理后台: http://localhost:8090"
    log_info "客户端命令端口: 4900"
    log_info "客户端 SSL 端口: 4993"
    log_info "动态代理端口: 5000-5100"
}

# 停止服务
cmd_stop() {
    log_step "停止 qiproxy 服务..."
    check_docker

    local COMPOSE=$(docker_compose_cmd)
    $COMPOSE down

    log_info "服务已停止"
}

# 重启服务
cmd_restart() {
    log_step "重启 qiproxy 服务..."
    check_docker

    local COMPOSE=$(docker_compose_cmd)
    $COMPOSE restart

    log_info "服务已重启"
}

# 查看状态
cmd_status() {
    log_step "查看容器状态..."
    check_docker

    docker ps --filter "name=qiproxy" --format "table {{.Names}}\t{{.Status}}\t{{.Ports}}"
}

# 查看日志
cmd_logs() {
    log_step "查看 qiproxy-server 日志..."
    check_docker

    local COMPOSE=$(docker_compose_cmd)
    $COMPOSE logs -f proxy-server
}

# 启动客户端（交互模式，便于调试）
cmd_client() {
    log_step "启动 qiproxy-client（交互模式）..."
    check_docker

    local COMPOSE=$(docker_compose_cmd)

    # 确保客户端配置目录存在
    mkdir -p proxy-client/conf

    log_warn "请确保 proxy-client/conf/config.properties 已正确配置"
    log_info "启动客户端容器..."

    # 使用 run 而非 up，以便交互式查看输出
    $COMPOSE --profile client run --rm proxy-client
}

# 查看帮助
cmd_help() {
    echo ""
    echo "============================================"
    echo "  qiproxy 一键部署脚本"
    echo "============================================"
    echo ""
    echo "用法: ./deploy.sh <命令>"
    echo ""
    echo "命令:"
    echo "  build     构建 Docker 镜像"
    echo "  start     启动服务端容器"
    echo "  stop      停止所有容器"
    echo "  restart   重启服务"
    echo "  status    查看容器运行状态"
    echo "  logs      查看服务端日志"
    echo "  client    启动客户端容器（交互模式）"
    echo "  help      显示帮助信息"
    echo ""
    echo "示例:"
    echo "  ./deploy.sh build   # 首次使用先构建镜像"
    echo "  ./deploy.sh start   # 启动服务端"
    echo "  ./deploy.sh client  # 启动客户端"
    echo ""
}

# 主流程
main() {
    case "${1:-help}" in
        build)
            cmd_build
            ;;
        start)
            cmd_start
            ;;
        stop)
            cmd_stop
            ;;
        restart)
            cmd_restart
            ;;
        status)
            cmd_status
            ;;
        logs)
            cmd_logs
            ;;
        client)
            cmd_client
            ;;
        help|*)
            cmd_help
            ;;
    esac
}

main "$@"
