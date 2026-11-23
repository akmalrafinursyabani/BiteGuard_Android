#!/usr/bin/env bash
set -euo pipefail

ENV=""
STATUS=""
REASON=""

while [[ $# -gt 0 ]]; do
  case "$1" in
    --env)    ENV="$2";    shift 2 ;;
    --status) STATUS="$2"; shift 2 ;;
    --reason) REASON="$2"; shift 2 ;;
    *) echo "Unknown argument: $1" >&2; exit 1 ;;
  esac
done

if [[ -z "$ENV" || -z "$STATUS" ]]; then
  echo "Usage: $0 --env <env> --status <status> [--reason <reason>]" >&2
  exit 1
fi

MARKER_DIR="build/deploy-markers"
TIMESTAMP=$(date -u '+%Y-%m-%dT%H:%M:%SZ')
MARKER_FILE="${MARKER_DIR}/deploy-${ENV}-${TIMESTAMP//:/}.json"

mkdir -p "$MARKER_DIR"

cat > "$MARKER_FILE" <<EOF
{
  "env": "${ENV}",
  "status": "${STATUS}",
  "reason": "${REASON}",
  "timestamp": "${TIMESTAMP}",
  "build_number": "${CIRCLE_BUILD_NUM:-local}",
  "branch": "${CIRCLE_BRANCH:-unknown}",
  "sha": "${CIRCLE_SHA1:-unknown}"
}
EOF

echo "Deploy marker written: $MARKER_FILE"
cat "$MARKER_FILE"
