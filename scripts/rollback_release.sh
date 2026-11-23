#!/usr/bin/env bash
set -euo pipefail

ENV=""
REASON=""
TAG=""

while [[ $# -gt 0 ]]; do
  case "$1" in
    --env)    ENV="$2";    shift 2 ;;
    --reason) REASON="$2"; shift 2 ;;
    --tag)    TAG="$2";    shift 2 ;;
    *) echo "Unknown argument: $1" >&2; exit 1 ;;
  esac
done

if [[ -z "$ENV" ]]; then
  echo "Usage: $0 --env <env> [--reason <reason>] [--tag <tag>]" >&2
  exit 1
fi

MARKER_DIR="build/deploy-markers"
TIMESTAMP=$(date -u '+%Y-%m-%dT%H:%M:%SZ')
MARKER_FILE="${MARKER_DIR}/rollback-${ENV}-${TIMESTAMP//:/}.json"

mkdir -p "$MARKER_DIR"

RESOLVED_TAG="${TAG:-$(git describe --tags --abbrev=0 2>/dev/null || echo 'unknown')}"

cat > "$MARKER_FILE" <<EOF
{
  "env": "${ENV}",
  "status": "ROLLED_BACK",
  "reason": "${REASON}",
  "rollback_tag": "${RESOLVED_TAG}",
  "timestamp": "${TIMESTAMP}",
  "build_number": "${CIRCLE_BUILD_NUM:-local}",
  "branch": "${CIRCLE_BRANCH:-unknown}",
  "sha": "${CIRCLE_SHA1:-unknown}"
}
EOF

echo "Rollback marker written: $MARKER_FILE"
cat "$MARKER_FILE"
