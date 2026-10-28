#!/usr/bin/env bash
# Runs the whole demo on this machine, without Docker:
#   registry (8761), jira-mcp x2 (8081, 8083), github-mcp (8082), then the demo agent.
# Prerequisites: Java 21, Maven 3.9+. Logs go to samples/logs/. Ctrl-C stops everything.
set -euo pipefail
cd "$(dirname "$0")"
export MCP_DISCOVERY_API_KEY="${MCP_DISCOVERY_API_KEY:-demo-key}"

if [ "${1:-}" != "--no-build" ]; then
  echo "Building the library and the samples..."
  (cd .. && mvn -B -q install -DskipTests)
  mvn -B -q package -DskipTests
fi

mkdir -p logs
pids=()
cleanup() { echo; echo "Stopping..."; kill "${pids[@]}" 2>/dev/null || true; wait 2>/dev/null || true; }
trap cleanup EXIT INT TERM

start() { # name jar [env...]
  local name=$1 jar=$2; shift 2
  env "$@" java -jar "$jar" > "logs/$name.log" 2>&1 &
  pids+=($!)
  echo "  started $name (log: samples/logs/$name.log)"
}

wait_for() { # url
  for _ in $(seq 1 60); do curl -fs "$1" >/dev/null 2>&1 && return 0; sleep 1; done
  echo "Timed out waiting for $1" >&2; exit 1
}

echo "Starting the registry..."
start registry registry/target/registry.jar
wait_for http://localhost:8761/actuator/health

echo "Starting the mock MCP servers..."
start jira-mcp-1 jira-mcp-mock/target/jira-mcp-mock.jar PORT=8081 MCP_INSTANCE_ID=jira-mcp-1
start jira-mcp-2 jira-mcp-mock/target/jira-mcp-mock.jar PORT=8083 MCP_INSTANCE_ID=jira-mcp-2 MOCK_LATENCY=300ms
start github-mcp github-mcp-mock/target/github-mcp-mock.jar PORT=8082 MCP_INSTANCE_ID=github-mcp-1

echo
echo "Dashboard: http://localhost:8761/   (API key: $MCP_DISCOVERY_API_KEY)"
echo "Running the demo agent (it waits for the registry to probe the tools, about 20 s)..."
java -jar demo-agent/target/demo-agent.jar

echo "The servers keep running so you can explore the dashboard. Press Ctrl-C to stop them."
wait
