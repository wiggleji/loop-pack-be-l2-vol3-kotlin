#!/usr/bin/env bash
#
# Week 9 Ranking System Benchmark Runner
#
# 동일한 k6 시나리오를 10회 반복 실행해서 분산/평균을 얻는다.
# 각 실행 결과는 ./k6/results/ranking-benchmark-{ISO8601}.txt 에 저장된다.
#
# Usage:
#   ./k6/run-ranking-benchmark.sh             # 기본 10회
#   ./k6/run-ranking-benchmark.sh 5           # 5회
#   BASE_URL=http://localhost:8080 ./k6/run-ranking-benchmark.sh
#
set -euo pipefail

REPEAT="${1:-10}"
K6_SCRIPT="${2:-k6/ranking-benchmark.js}"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"

echo "=========================================="
echo "Ranking Benchmark — $REPEAT runs"
echo "Script: $K6_SCRIPT"
echo "BASE_URL: ${BASE_URL:-http://localhost:8080}"
echo "=========================================="

for i in $(seq 1 "$REPEAT"); do
  echo ""
  echo "--- Run $i / $REPEAT ---"
  (cd "$PROJECT_ROOT" && k6 run "$K6_SCRIPT")
  sleep 2
done

echo ""
echo "Done. Results saved under k6/results/"
ls -1t "$PROJECT_ROOT/k6/results/" | grep "^ranking-benchmark-" | head -n "$REPEAT"
