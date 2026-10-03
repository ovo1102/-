#!/usr/bin/env bash
# ============================================================
# 本地文件完整性哈希校验工具 - Linux/macOS 启动脚本
# 用法：chmod +x run.sh && ./run.sh
# ============================================================
set -e

# 解析脚本所在目录，保证从任意路径调用都能定位 jar
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
JAR="$SCRIPT_DIR/target/hash-check-tool-1.0.0.jar"

if [ ! -f "$JAR" ]; then
    echo "[错误] 未找到可执行 jar：$JAR" >&2
    echo "请先在项目根目录执行：mvn clean package" >&2
    exit 1
fi

if ! command -v java >/dev/null 2>&1; then
    echo "[错误] 未检测到 java 命令，请先安装 JRE 8 或更高版本。" >&2
    exit 1
fi

exec java -jar "$JAR"
