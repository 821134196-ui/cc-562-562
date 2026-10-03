#!/usr/bin/env bash
# 运行后端测试（含三个必测场景：容量不足改普通杯 / 并发换杯重计价 / 退款优惠处理）
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

if ! command -v java >/dev/null 2>&1 && [ -d "$ROOT/.tools/jdk" ]; then
  export JAVA_HOME="$ROOT/.tools/jdk"
  export PATH="$JAVA_HOME/bin:$PATH"
fi
if ! command -v mvn >/dev/null 2>&1 && [ -d "$ROOT/.tools/maven" ]; then
  export PATH="$ROOT/.tools/maven/bin:$PATH"
fi
MVN_OPTS=()
[ -f "$ROOT/.tools/maven-settings.xml" ] && MVN_OPTS=(-s "$ROOT/.tools/maven-settings.xml")

cd "$ROOT/backend"
exec mvn "${MVN_OPTS[@]}" test
