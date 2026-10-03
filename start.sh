#!/usr/bin/env bash
# 一条命令启动整个系统：构建前端 → 打包后端 → 启动（前后端同端口 8080）
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

# ---- 工具链：优先系统 java/mvn，否则用仓库内 .tools ----
if ! command -v java >/dev/null 2>&1 && [ -d "$ROOT/.tools/jdk" ]; then
  export JAVA_HOME="$ROOT/.tools/jdk"
  export PATH="$JAVA_HOME/bin:$PATH"
fi
if ! command -v mvn >/dev/null 2>&1 && [ -d "$ROOT/.tools/maven" ]; then
  export PATH="$ROOT/.tools/maven/bin:$PATH"
fi
MVN_OPTS=()
[ -f "$ROOT/.tools/maven-settings.xml" ] && MVN_OPTS=(-s "$ROOT/.tools/maven-settings.xml")

command -v java >/dev/null 2>&1 || { echo "需要 Java 17+（或 .tools/jdk）"; exit 1; }
command -v mvn  >/dev/null 2>&1 || { echo "需要 Maven（或 .tools/maven）"; exit 1; }

# ---- 前端：安装依赖并构建到后端静态资源目录 ----
cd "$ROOT/frontend"
[ -d node_modules ] || npm install
npm run build

# ---- 后端：打包并运行 ----
mkdir -p "$ROOT/data"
cd "$ROOT/backend"
mvn "${MVN_OPTS[@]}" -q package -DskipTests

echo ""
echo "  收银台:  http://localhost:8080"
echo "  API:     http://localhost:8080/api/menu"
echo ""
cd "$ROOT"   # SQLite 文件落在 ./data/cupcheck.db
exec java -jar backend/target/quarkus-app/quarkus-run.jar
