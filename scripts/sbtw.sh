#!/bin/bash
# ============================================================================
# SBTW - SBT Wrapper for WSL
# ============================================================================
#
# 用法:
#   ./scripts/sbtw.sh [options] <command> [args...]
#
# 选项:
#   -p, --project <dir>   指定项目目录 (默认: 脚本所在项目的父目录)
#   -h, --help            显示帮助
#
# 环境变量:
#   SBTW_PROJECT_DIR      项目目录 (优先级低于 -p 选项)
#
# 命令:
#   run <sbt-cmd>     运行任意 sbt 命令
#   gen [module]      生成 Verilog (自动发现顶层模块，或指定模块名)
#   test [class]      运行测试套件 (可选指定测试类)
#   clean             清理构建产物
#   check             检查环境状态
#   setup             配置开发环境
#   list              列出可用的顶层模块和测试类
#
# 示例:
#   ./scripts/sbtw.sh gen                          # 自动发现顶层
#   ./scripts/sbtw.sh gen pcie.PcieControllerGen   # 指定模块
#   ./scripts/sbtw.sh test                         # 运行所有测试
#   ./scripts/sbtw.sh test pcie.PcieControllerTest # 指定测试类
#   ./scripts/sbtw.sh -p /path/to/project gen
#   SBTW_PROJECT_DIR=/path/to/project ./scripts/sbtw.sh test
#   ./scripts/sbtw.sh run "compile"
#
# 问题背景:
#   Windows 安装的 sbt 在 WSL 中直接运行会出现路径问题
#   通过 cmd.exe 调用 Windows 版本的 sbt 解决
#
# 支持的顶层模块模式:
#   1. object XxxGen extends App { ... }           (PcieController 风格)
#   2. object Xxx { def main(args: Array[String])  (UartController 风格)
# ============================================================================

set -e

# 颜色定义
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

# 默认项目目录 (脚本所在项目的父目录)
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
DEFAULT_PROJECT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"

# 项目目录: 优先级 命令行选项 > 环境变量 > 默认值
PROJECT_DIR="${SBTW_PROJECT_DIR:-$DEFAULT_PROJECT_DIR}"

# ============================================================================
# 工具函数
# ============================================================================

print_info() { echo -e "${BLUE}[i]${NC} $1"; }
print_ok() { echo -e "${GREEN}[✓]${NC} $1"; }
print_warn() { echo -e "${YELLOW}[!]${NC} $1"; }
print_err() { echo -e "${RED}[✗]${NC} $1"; }

run_sbt() {
    local cmd="$1"
    print_info "项目目录: $PROJECT_DIR"
    print_info "执行: sbt $cmd"
    echo "----------------------------------------"
    cd "$PROJECT_DIR"
    printf "%s\n" "$cmd" | cmd.exe /c "sbt" 2>&1
    echo "----------------------------------------"
}

# 自动发现顶层模块名
# 支持两种模式:
#   1. object XxxGen extends App (或 object Xxx extends App)
#   2. object Xxx { def main(args: Array[String])
# 排除: *Sim 后缀的仿真对象
find_top_modules() {
    local src_dir="$PROJECT_DIR/src/main/scala"
    if [ ! -d "$src_dir" ]; then
        return
    fi

    # 模式1: object XxxGen extends App (优先匹配 *Gen)
    grep -rhP '^\s*object\s+\w+Gen\s+extends\s+App' "$src_dir" 2>/dev/null | \
        sed -E 's/.*object\s+(\w+Gen)\s+extends\s+App.*/\1/' | grep -v 'Sim$'

    # 模式1b: object Xxx extends App (非 Gen 后缀，检查是否有 generateVerilog)
    local candidates
    candidates=$(grep -rhl 'extends App' "$src_dir" 2>/dev/null)
    for f in $candidates; do
        local obj
        obj=$(grep -P '^\s*object\s+\w+\s+extends\s+App' "$f" 2>/dev/null | \
              sed -E 's/.*object\s+(\w+)\s+extends\s+App.*/\1/' | \
              grep -v 'Gen$' | grep -v 'Sim$' | head -1)
        if [ -n "$obj" ] && grep -q 'generateVerilog' "$f" 2>/dev/null; then
            echo "$obj"
        fi
    done

    # 模式2: object Xxx { def main(args: Array[String]) ... } 且包含 generateVerilog
    local main_candidates
    main_candidates=$(grep -rhl 'def main.*Array\[String\]' "$src_dir" 2>/dev/null)
    for f in $main_candidates; do
        if grep -q 'generateVerilog' "$f" 2>/dev/null; then
            grep -P '^\s*object\s+\w+\s*\{' "$f" 2>/dev/null | \
                sed -E 's/.*object\s+(\w+)\s*\{.*/\1/' | grep -v 'Sim$'
        fi
    done
}

# 获取对象的完整包名
get_full_name() {
    local obj_name="$1"
    local src_dir="$PROJECT_DIR/src/main/scala"
    if [ ! -d "$src_dir" ]; then
        echo "$obj_name"
        return
    fi
    # 找到定义该对象的文件，提取包名
    local file=$(grep -rl "object $obj_name" "$src_dir" 2>/dev/null | head -1)
    if [ -n "$file" ]; then
        local pkg=$(grep -E '^package\s+' "$file" | head -1 | sed 's/package\s*//')
        if [ -n "$pkg" ]; then
            echo "$pkg.$obj_name"
            return
        fi
    fi
    echo "$obj_name"
}

# 发现测试类
find_test_classes() {
    local test_dir="$PROJECT_DIR/src/test/scala"
    if [ ! -d "$test_dir" ]; then
        return
    fi
    grep -rhP 'class\s+\w+Test\s+extends\s+(AnyFunSuite|FlatSpec|AnyFlatSpec)' "$test_dir" 2>/dev/null | \
        sed -E 's/class\s+(\w+Test).*/\1/' | sort -u
}

# ============================================================================
# 命令实现
# ============================================================================

cmd_run() {
    if [ -z "$1" ]; then
        print_err "缺少 sbt 命令"
        echo "用法: $0 run \"<sbt命令>\""
        exit 1
    fi
    run_sbt "$1"
}

cmd_gen() {
    local module="$1"

    if [ -z "$module" ]; then
        # 自动发现顶层模块
        local modules
        modules=$(find_top_modules | sort -u)

        if [ -z "$modules" ]; then
            print_err "未找到顶层模块"
            echo "请手动指定: $0 gen <module>"
            echo ""
            echo "支持的顶层模块模式:"
            echo "  1. object XxxGen extends App { ... generateVerilog ... }"
            echo "  2. object Xxx { def main(args: Array[String]) = ... generateVerilog ... }"
            exit 1
        fi

        local count
        count=$(echo "$modules" | wc -l)

        if [ "$count" -eq 1 ]; then
            local obj_name
            obj_name=$(echo "$modules" | head -1)
            module=$(get_full_name "$obj_name")
            print_info "自动发现顶层: $module"
        else
            print_warn "发现多个顶层模块:"
            while IFS= read -r m; do
                [ -n "$m" ] && echo "  - $(get_full_name "$m")"
            done <<< "$modules"
            echo ""
            echo "请指定模块: $0 gen <module>"
            exit 1
        fi
    fi

    print_info "生成 Verilog: $module"
    run_sbt "runMain $module"

    # 尝试推断输出文件
    local base_name=$(echo "$module" | sed 's/.*\.//;s/Gen$//')
    local v_file="$PROJECT_DIR/rtl/${base_name}.v"
    if [ -f "$v_file" ]; then
        print_ok "Verilog 生成完成: $v_file"
    else
        print_ok "Verilog 生成完成 (查看 rtl/ 目录)"
    fi
}

cmd_test() {
    local test_class="$1"

    if [ -z "$test_class" ]; then
        # 运行所有测试
        print_info "运行所有测试..."
        run_sbt "test"
    else
        print_info "运行测试: $test_class"
        run_sbt "testOnly $test_class"
    fi
    print_ok "测试完成"
}

cmd_list() {
    echo "======================================"
    echo "    可用的顶层模块"
    echo "======================================"
    local modules
    modules=$(find_top_modules | sort -u)

    if [ -z "$modules" ]; then
        echo "  (未找到)"
    else
        while IFS= read -r m; do
            [ -n "$m" ] && echo "  $(get_full_name "$m")"
        done <<< "$modules"
    fi

    echo ""
    echo "======================================"
    echo "    可用的测试类"
    echo "======================================"
    local tests
    tests=$(find_test_classes)

    if [ -z "$tests" ]; then
        echo "  (未找到)"
    else
        while IFS= read -r t; do
            [ -n "$t" ] && echo "  $t"
        done <<< "$tests"
    fi
}

cmd_clean() {
    print_info "清理构建产物..."
    run_sbt "clean"
    print_ok "清理完成"
}

cmd_check() {
    echo "======================================"
    echo "    WSL 开发环境检查"
    echo "======================================"
    echo ""

    # Java
    echo "检查 Java 环境..."
    if java -version &>/dev/null; then
        JAVA_VER=$(java -version 2>&1 | head -1 | cut -d'"' -f2 | cut -d'.' -f1)
        print_ok "Java $JAVA_VER 已安装"
    else
        print_err "Java 未安装"
    fi
    echo ""

    # Windows sbt
    echo "检查 Windows sbt..."
    if cmd.exe /c "sbt --version" &>/dev/null; then
        SBT_VER=$(cmd.exe /c "sbt --version" 2>&1 | grep "sbt script version" | awk '{print $NF}' || echo "unknown")
        print_ok "Windows sbt $SBT_VER 已安装"
    else
        print_warn "Windows sbt 未安装或不可用"
    fi
    echo ""

    # Coursier
    echo "检查 Coursier..."
    if command -v cs &>/dev/null || [ -f ~/bin/cs ] || [ -f ~/.local/share/coursier/bin/cs ]; then
        print_ok "Coursier 已安装"
    else
        print_warn "Coursier 未安装"
    fi
    echo ""

    echo "======================================"
    echo "    当前配置"
    echo "======================================"
    echo ""
    echo "项目目录: $PROJECT_DIR"
    echo ""
    echo "======================================"
    echo "    推荐操作"
    echo "======================================"
    echo ""
    echo "1. 使用本脚本 (无需额外配置):"
    echo "   ./scripts/sbtw.sh gen"
    echo "   ./scripts/sbtw.sh test"
    echo ""
    echo "2. 指定其他项目:"
    echo "   ./scripts/sbtw.sh -p /path/to/project run \"compile\""
    echo "   SBTW_PROJECT_DIR=/path/to/project ./scripts/sbtw.sh test"
    echo ""
    echo "3. 安装 Coursier (推荐，原生 Linux sbt):"
    echo "   ./scripts/sbtw.sh setup --coursier"
    echo ""
}

cmd_setup() {
    case "${1:-}" in
        --coursier)
            print_info "安装 Coursier..."
            mkdir -p ~/bin ~/.local/share/coursier/bin

            echo "正在下载 Coursier..."
            if curl -fL "https://github.com/coursier/coursier/releases/latest/download/cs-x86_64-pc-linux.gz" | gzip -d > ~/bin/cs; then
                chmod +x ~/bin/cs
                print_ok "Coursier 下载完成"
            else
                print_err "Coursier 下载失败"
                print_warn "可能是网络问题，请尝试手动下载或使用代理"
                exit 1
            fi

            echo "正在安装 sbt 和 scala..."
            ~/bin/cs setup -y --install --jvm --sbt --scala:2.11.12

            if ! grep -q "coursier/bin" ~/.bashrc 2>/dev/null; then
                echo '' >> ~/.bashrc
                echo '# Coursier binaries' >> ~/.bashrc
                echo 'export PATH="$HOME/.local/share/coursier/bin:$HOME/bin:$PATH"' >> ~/.bashrc
                print_ok "PATH 已更新"
            fi

            echo ""
            echo "请运行以下命令使配置生效:"
            echo "  source ~/.bashrc"
            ;;

        --alias)
            print_info "配置 bash 别名..."
            if grep -q "alias sbt=" ~/.bashrc 2>/dev/null; then
                print_warn "别名已存在于 ~/.bashrc"
            else
                echo '' >> ~/.bashrc
                echo '# SBT alias for WSL' >> ~/.bashrc
                echo 'alias sbt='\''cmd.exe /c "sbt"'\''' >> ~/.bashrc
                print_ok "别名已添加到 ~/.bashrc"
            fi

            echo ""
            echo "请运行以下命令使配置生效:"
            echo "  source ~/.bashrc"
            ;;

        *)
            echo "用法: $0 setup [--coursier | --alias]"
            echo ""
            echo "选项:"
            echo "  --coursier  安装 Coursier 并配置原生 sbt（推荐）"
            echo "  --alias     设置 bash 别名（快速方案）"
            exit 1
            ;;
    esac
}

show_help() {
    cat << EOF
SBTW - SBT Wrapper for WSL

用法:
  $0 [options] <command> [args...]

选项:
  -p, --project <dir>   指定项目目录
                        (默认: $DEFAULT_PROJECT_DIR)
  -h, --help            显示帮助

环境变量:
  SBTW_PROJECT_DIR      项目目录 (优先级低于 -p 选项)

命令:
  run <cmd>       运行任意 sbt 命令
  gen [module]    生成 Verilog (自动发现顶层模块，或指定模块)
  test [class]    运行测试 (可选指定测试类)
  clean           清理构建产物
  check           检查环境状态
  setup           配置开发环境
  list            列出可用的顶层模块和测试类

Setup 选项:
  --coursier    安装 Coursier 并配置原生 sbt（推荐）
  --alias       设置 bash 别名（快速方案）

支持的顶层模块模式:
  1. object XxxGen extends App { ... generateVerilog ... }
  2. object Xxx { def main(args: Array[String]) = ... generateVerilog ... }

示例:
  # 自动发现顶层模块
  $0 gen

  # 指定模块
  $0 gen pcie.PcieControllerGen

  # 运行所有测试
  $0 test

  # 运行指定测试
  $0 test pcie.PcieControllerTest

  # 指定其他项目
  $0 -p /path/to/project gen

EOF
}

# ============================================================================
# 参数解析
# ============================================================================

while [[ $# -gt 0 ]]; do
    case "$1" in
        -p|--project)
            PROJECT_DIR="$2"
            shift 2
            ;;
        -h|--help)
            show_help
            exit 0
            ;;
        -*)
            print_err "未知选项: $1"
            show_help
            exit 1
            ;;
        *)
            break
            ;;
    esac
done

# 验证项目目录
if [ ! -d "$PROJECT_DIR" ]; then
    print_err "项目目录不存在: $PROJECT_DIR"
    exit 1
fi

# ============================================================================
# 命令分发
# ============================================================================

case "${1:-}" in
    run)   shift; cmd_run "$@" ;;
    gen)   shift; cmd_gen "$@" ;;
    test)  shift; cmd_test "$@" ;;
    clean) cmd_clean ;;
    check) cmd_check ;;
    setup) shift; cmd_setup "$@" ;;
    list)  cmd_list ;;
    "")
        print_err "缺少命令"
        show_help
        exit 1
        ;;
    *)
        print_err "未知命令: $1"
        show_help
        exit 1
        ;;
esac
