#!/usr/bin/env bash
# Starts this checkout's backend and Vite; Ctrl+C stops only these two processes.
set -euo pipefail
cd "$(dirname "$0")"
if [[ -f .env ]]; then
  set -a
  source .env
  set +a
fi
export SERVER_PORT="${SERVER_PORT:-8080}"
export FRONTEND_PORT="${FRONTEND_PORT:-5173}"
export APP_CORS_ALLOWED_ORIGIN="${APP_CORS_ALLOWED_ORIGIN:-http://localhost:$FRONTEND_PORT}"
export API_PROXY_TARGET="http://localhost:$SERVER_PORT"
for executable in java node curl; do
  command -v "$executable" >/dev/null || { echo "缺少 $executable，请先安装。"; exit 1; }
done
[[ -d frontend/node_modules ]] || { echo '请先在 frontend 执行 pnpm install --frozen-lockfile'; exit 1; }
for port in "$SERVER_PORT" "$FRONTEND_PORT"; do
  if curl --noproxy '*' --connect-timeout 1 -s -o /dev/null "http://localhost:$port/"; then
    echo "端口 $port 已有 HTTP 服务。请更换端口或停止原服务。"
    exit 1
  fi
done
mkdir -p .local
if command -v mvn >/dev/null; then
  mvn -f backend/pom.xml -DskipTests package > .local/build.log 2>&1 || { tail -40 .local/build.log; exit 1; }
else
  (cd backend && sh mvnw -DskipTests package) > .local/build.log 2>&1 || { tail -40 .local/build.log; exit 1; }
fi
backend_pid=''
frontend_pid=''
cleanup() {
  trap - EXIT INT TERM
  [[ -z "$frontend_pid" ]] || kill "$frontend_pid" 2>/dev/null || true
  [[ -z "$backend_pid" ]] || kill "$backend_pid" 2>/dev/null || true
  wait 2>/dev/null || true
}
trap cleanup EXIT
trap 'exit 130' INT TERM
java -jar backend/target/tech-blog-backend-0.1.0-SNAPSHOT.jar > .local/backend.log 2>&1 &
backend_pid=$!
ready=false
for ((attempt=0; attempt<60; attempt++)); do
  kill -0 "$backend_pid" 2>/dev/null || { tail -40 .local/backend.log; exit 1; }
  if curl --noproxy '*' -fsS "http://localhost:$SERVER_PORT/api/health" >/dev/null 2>&1; then ready=true; break; fi
  sleep 1
done
if [[ "$ready" != true ]]; then
  echo '后端未就绪。请检查数据库连接配置，详细日志见 .local/backend.log'
  exit 1
fi
(cd frontend && exec node node_modules/vite/bin/vite.js --port "$FRONTEND_PORT") > .local/frontend.log 2>&1 &
frontend_pid=$!
ready=false
for ((attempt=0; attempt<20; attempt++)); do
  kill -0 "$frontend_pid" 2>/dev/null || { cat .local/frontend.log; exit 1; }
  if curl --noproxy '*' -fsS "http://localhost:$FRONTEND_PORT/" >/dev/null 2>&1; then ready=true; break; fi
  sleep 1
done
[[ "$ready" == true ]] || { cat .local/frontend.log; exit 1; }
echo "Community Forum 已启动：http://localhost:$FRONTEND_PORT"
echo "后端：http://localhost:$SERVER_PORT/api/health"
echo '日志：.local/backend.log 与 .local/frontend.log；按 Ctrl+C 停止。'
# Exit and clean up both processes if either service stops unexpectedly.
while kill -0 "$backend_pid" 2>/dev/null && kill -0 "$frontend_pid" 2>/dev/null; do sleep 2; done
