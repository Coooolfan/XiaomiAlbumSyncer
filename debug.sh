#!/bin/bash

# Xiaomi Album Syncer - Debug 模式启动脚本
# 用于快速启动开发调试环境

set -e  # 遇到错误立即退出

# 默认配置
BUILD_FRONTEND=false
FRONTEND_DEV=false

# 颜色定义
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# 打印带颜色的消息
print_info() {
    echo -e "${BLUE}[INFO]${NC} $1"
}

print_success() {
    echo -e "${GREEN}[SUCCESS]${NC} $1"
}

print_warning() {
    echo -e "${YELLOW}[WARNING]${NC} $1"
}

print_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

# 打印标题
print_header() {
    echo ""
    echo -e "${GREEN}========================================${NC}"
    echo -e "${GREEN}  Xiaomi Album Syncer - Debug Mode${NC}"
    echo -e "${GREEN}========================================${NC}"
    echo ""
}

# 显示使用帮助
show_usage() {
    echo "用法: $0 [选项]"
    echo ""
    echo "选项:"
    echo "  -f, --frontend       构建前端并集成到后端"
    echo "  -d, --frontend-dev   启动前端开发服务器（推荐）"
    echo "  -h, --help          显示此帮助信息"
    echo ""
    echo "示例:"
    echo "  $0                   # 仅启动后端（API 模式）"
    echo "  $0 -f                # 构建前端并启动完整应用"
    echo "  $0 -d                # 启动后端 + 前端开发服务器"
    echo ""
}

# 解析命令行参数
parse_args() {
    while [[ $# -gt 0 ]]; do
        case $1 in
            -f|--frontend)
                BUILD_FRONTEND=true
                shift
                ;;
            -d|--frontend-dev)
                FRONTEND_DEV=true
                shift
                ;;
            -h|--help)
                show_usage
                exit 0
                ;;
            *)
                print_error "未知选项: $1"
                show_usage
                exit 1
                ;;
        esac
    done
}

# 检查 Java 环境
check_java() {
    print_info "检查 Java 环境..."
    
    if ! command -v java &> /dev/null; then
        print_error "未找到 Java 命令，请先安装 Java 25 或 GraalVM JDK 25"
        exit 1
    fi
    
    JAVA_VERSION=$(java -version 2>&1 | head -n 1 | cut -d'"' -f2 | cut -d'.' -f1)
    print_success "Java 版本: $(java -version 2>&1 | head -n 1)"
    
    if [ "$JAVA_VERSION" -lt 21 ]; then
        print_warning "建议使用 Java 25，当前版本可能不兼容"
    fi
}

# 检查 Gradle
check_gradle() {
    print_info "检查 Gradle..."
    
    if [ ! -f "server/gradlew" ]; then
        print_error "未找到 Gradle Wrapper，请确保在项目根目录运行此脚本"
        exit 1
    fi
    
    print_success "Gradle Wrapper 已就绪"
}

# 检查 Node.js（如果需要前端）
check_node() {
    if [ "$BUILD_FRONTEND" = true ] || [ "$FRONTEND_DEV" = true ]; then
        print_info "检查 Node.js 环境..."
        
        if ! command -v node &> /dev/null; then
            print_error "未找到 Node.js，请先安装 Node.js 24+"
            exit 1
        fi
        
        NODE_VERSION=$(node -v)
        print_success "Node.js 版本: $NODE_VERSION"
        
        # 检查 yarn
        if ! command -v yarn &> /dev/null; then
            print_error "未找到 Yarn，请运行: npm install -g corepack && corepack enable"
            exit 1
        fi
        
        print_success "Yarn 已就绪"
    fi
}

# 构建前端
build_frontend() {
    if [ "$BUILD_FRONTEND" = true ]; then
        print_info "构建前端资源..."
        
        cd web
        
        # 安装依赖
        if [ ! -d "node_modules" ]; then
            print_info "安装前端依赖..."
            yarn install
        fi
        
        # 构建
        print_info "编译前端代码..."
        yarn build
        
        # 复制到后端静态资源目录
        print_info "复制静态资源到后端..."
        mkdir -p ../server/src/main/resources/static
        cp -r dist/* ../server/src/main/resources/static/
        
        cd ..
        print_success "前端构建完成"
    fi
}

# 启动前端开发服务器
start_frontend_dev() {
    if [ "$FRONTEND_DEV" = true ]; then
        print_info "启动前端开发服务器..."
        
        cd web
        
        # 安装依赖
        if [ ! -d "node_modules" ]; then
            print_info "安装前端依赖..."
            yarn install
        fi
        
        # 后台启动开发服务器
        print_info "前端开发服务器启动中（端口 5173）..."
        yarn dev > ../frontend-dev.log 2>&1 &
        FRONTEND_PID=$!
        
        cd ..
        
        # 等待前端服务器启动
        sleep 3
        
        if ps -p $FRONTEND_PID > /dev/null; then
            print_success "前端开发服务器已启动 (PID: $FRONTEND_PID)"
            echo $FRONTEND_PID > .frontend-dev.pid
        else
            print_error "前端开发服务器启动失败，查看 frontend-dev.log"
            exit 1
        fi
    fi
}

# 清理旧的进程
cleanup_processes() {
    print_info "清理旧的进程..."
    
    # 查找并杀死占用 8080 端口的进程
    if lsof -Pi :8080 -sTCP:LISTEN -t >/dev/null 2>&1; then
        print_warning "端口 8080 被占用，正在清理..."
        lsof -ti:8080 | xargs kill -9 2>/dev/null || true
        sleep 1
        print_success "端口已释放"
    fi
}

# 停止 Gradle Daemon
stop_gradle_daemon() {
    print_info "停止 Gradle Daemon..."
    cd server
    ./gradlew --stop 2>/dev/null || true
    cd ..
    print_success "Gradle Daemon 已停止"
}

# 启动后端服务
start_backend() {
    print_info "启动后端服务（Debug 模式）..."
    echo ""
    print_info "日志输出："
    echo -e "${YELLOW}----------------------------------------${NC}"
    
    cd server
    
    # 设置 JVM 参数以启用调试和详细日志
    export JAVA_OPTS="-Xdebug -Xrunjdwp:transport=dt_socket,server=y,suspend=n,address=*:5005"
    export SOLON_ENV=dev
    
    # 运行应用并显示日志
    ./gradlew run --console=plain --quiet 2>&1 | while IFS= read -r line; do
        # 根据日志级别添加颜色
        if echo "$line" | grep -q "ERROR"; then
            echo -e "${RED}$line${NC}"
        elif echo "$line" | grep -q "WARN"; then
            echo -e "${YELLOW}$line${NC}"
        elif echo "$line" | grep -q "INFO"; then
            echo -e "${GREEN}$line${NC}"
        else
            echo "$line"
        fi
    done
}

# 信号处理：优雅退出
cleanup_on_exit() {
    echo ""
    print_warning "收到退出信号，正在清理..."
    
    # 停止前端开发服务器
    if [ -f ".frontend-dev.pid" ]; then
        FRONTEND_PID=$(cat .frontend-dev.pid)
        if ps -p $FRONTEND_PID > /dev/null 2>&1; then
            print_info "停止前端开发服务器..."
            kill $FRONTEND_PID 2>/dev/null || true
        fi
        rm -f .frontend-dev.pid
    fi
    
    stop_gradle_daemon
    print_success "清理完成，再见！"
    exit 0
}

# 主函数
main() {
    # 解析参数
    parse_args "$@"
    
    # 设置信号处理
    trap cleanup_on_exit SIGINT SIGTERM
    
    # 打印标题
    print_header
    
    # 检查环境
    check_java
    check_gradle
    check_node
    
    # 清理环境
    cleanup_processes
    stop_gradle_daemon
    
    # 构建前端（如果需要）
    build_frontend
    
    # 启动前端开发服务器（如果需要）
    start_frontend_dev
    
    echo ""
    print_success "环境检查完成，准备启动..."
    echo ""
    print_info "应用信息："
    
    if [ "$FRONTEND_DEV" = true ]; then
        echo "  - 前端地址: http://localhost:5173 (开发服务器)"
        echo "  - 后端地址: http://localhost:8080"
    elif [ "$BUILD_FRONTEND" = true ]; then
        echo "  - 应用地址: http://localhost:8080"
    else
        echo "  - API 地址: http://localhost:8080/api"
        echo "  - 提示: 使用 -d 参数启动前端开发服务器"
    fi
    
    echo "  - 调试端口: 5005 (JDWP)"
    echo ""
    print_info "按 Ctrl+C 停止应用"
    echo ""
    sleep 2
    
    # 启动后端
    start_backend
}

# 运行主函数
main "$@"
