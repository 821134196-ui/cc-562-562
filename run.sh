#!/usr/bin/env bash
# 一条本地命令启动：构建前端（如需）+ 打包 + 运行 Quarkus（SQLite + 本地模拟支付）
# 用法：./run.sh            启动（访问 http://localhost:8080）
#       ./run.sh --dev     前端热更新开发模式（需两个终端，见 README）
set -euo pipefail
cd "$(dirname "$0")"

# ---- 定位 JDK（优先系统 JAVA_HOME；否则用项目下载到 ~/tools 的 JDK17） ----
if [ -z "${JAVA_HOME:-}" ] || [ ! -x "${JAVA_HOME}/bin/java" ]; then
  CANDIDATE="$(ls -d "$HOME"/tools/jdk-17* 2>/dev/null | head -1 || true)"
  if [ -n "$CANDIDATE" ]; then export JAVA_HOME="$CANDIDATE"; fi
fi
if [ -z "${JAVA_HOME:-}" ] || [ ! -x "${JAVA_HOME}/bin/java" ]; then
  echo "未找到 JDK 17。请安装 openjdk-17，或下载 Temurin 17 解压到 ~/tools/。" >&2
  exit 1
fi
MVN="mvn"
if ! command -v mvn >/dev/null 2>&1; then
  BUNDLED_MVN="$(ls -d "$HOME"/tools/apache-maven-*/bin/mvn 2>/dev/null | head -1 || true)"
  if [ -n "$BUNDLED_MVN" ]; then MVN="$BUNDLED_MVN"; else
    echo "未找到 Maven。请安装 maven，或解压 apache-maven 到 ~/tools/。" >&2; exit 1
  fi
fi
export PATH="$JAVA_HOME/bin:$(dirname "$(command -v "$MVN" 2>/dev/null || echo "$MVN")"):$PATH"

if [ "${1:-}" = "--dev" ]; then
  echo "开发模式：先启动后端 (8080)，再在另一个终端执行 (cd frontend && npm install && npm run dev) 打开 5173"
  exec "$MVN" quarkus:dev
fi

# ---- 前端：node_modules / 构建产物缺失时自动构建（输出进后端静态资源目录） ----
if [ ! -f src/main/resources/META-INF/resources/index.html ]; then
  echo ">> 构建 Svelte 前端……"
  (cd frontend && [ -d node_modules ] || npm install --no-audit --no-fund)
  (cd frontend && npm run build)
fi

echo ">> 打包并启动 Quarkus（SQLite 文件: ${POS_DB_PATH:-pos.db}）……"
exec "$MVN" -q quarkus:run -Dquarkus.http.host=0.0.0.0
