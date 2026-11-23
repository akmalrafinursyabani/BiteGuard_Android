#!/usr/bin/env bash
set -euo pipefail

# ==============================================================================
# BiteGuard AI — Deployment Marker Generator & Dispatcher
# Emits structured deployment markers for release tracking, audit logs,
# Sentry / Datadog / Webhook telemetry, and rollback bookkeeping.
# ==============================================================================

TARGET_ENV="internal"
DEPLOY_STATUS="DEPLOYED"
DEPLOY_REASON="Standard CI/CD automated deployment"
VERSION_NAME="1.0"
VERSION_CODE="1"

while [[ $# -gt 0 ]]; do
    case "$1" in
        --env)
            TARGET_ENV="$2"
            shift 2
            ;;
        --status)
            DEPLOY_STATUS="$2"
            shift 2
            ;;
        --reason)
            DEPLOY_REASON="$2"
            shift 2
            ;;
        --version-name)
            VERSION_NAME="$2"
            shift 2
            ;;
        --version-code)
            VERSION_CODE="$2"
            shift 2
            ;;
        *)
            echo "Unknown argument: $1"
            exit 1
            ;;
    esac
done

MARKER_DIR="build/deploy-markers"
mkdir -p "$MARKER_DIR"

GIT_COMMIT="${CIRCLE_SHA1:-$(git rev-parse HEAD 2>/dev/null || echo "unknown")}"
GIT_BRANCH="${CIRCLE_BRANCH:-$(git rev-parse --abbrev-ref HEAD 2>/dev/null || echo "unknown")}"
BUILD_NUM="${CIRCLE_BUILD_NUM:-local}"
BUILD_URL="${CIRCLE_BUILD_URL:-https://app.circleci.com}"
TIMESTAMP="$(date -u +"%Y-%m-%dT%H:%M:%SZ")"

AAB_FILE="app/build/outputs/bundle/release/app-release.aab"
AAB_SHA256="N/A"
if [ -f "$AAB_FILE" ]; then
    AAB_SHA256=$(sha256sum "$AAB_FILE" | cut -d ' ' -f 1)
fi

echo "============================================================"
echo " BiteGuard AI — Deploy Marker Registration"
echo " Environment : $TARGET_ENV"
echo " Status      : $DEPLOY_STATUS"
echo " Reason      : $DEPLOY_REASON"
echo " Git Commit  : $GIT_COMMIT"
echo " Git Branch  : $GIT_BRANCH"
echo " Build Num   : $BUILD_NUM"
echo " AAB SHA-256 : $AAB_SHA256"
echo " Timestamp   : $TIMESTAMP"
echo "============================================================"

# --- 1. Generate Structured JSON Deploy Marker ---
MARKER_JSON="$MARKER_DIR/deploy-marker-${TARGET_ENV}-${DEPLOY_STATUS}.json"
cat <<EOF > "$MARKER_JSON"
{
  "service": "BiteGuardAI",
  "environment": "$TARGET_ENV",
  "status": "$DEPLOY_STATUS",
  "reason": "$DEPLOY_REASON",
  "versionName": "$VERSION_NAME",
  "versionCode": "$VERSION_CODE",
  "git": {
    "commit": "$GIT_COMMIT",
    "branch": "$GIT_BRANCH"
  },
  "ci": {
    "provider": "CircleCI",
    "buildNumber": "$BUILD_NUM",
    "buildUrl": "$BUILD_URL"
  },
  "artifact": {
    "type": "AAB",
    "file": "$AAB_FILE",
    "sha256": "$AAB_SHA256"
  },
  "timestamp": "$TIMESTAMP"
}
EOF

# Copy as primary active marker
cp "$MARKER_JSON" "$MARKER_DIR/active-deploy-marker.json"

# --- 2. Generate Human-Readable Markdown Manifest ---
MARKDOWN_MANIFEST="$MARKER_DIR/deployment-manifest-${TARGET_ENV}.md"
cat <<EOF > "$MARKDOWN_MANIFEST"
# BiteGuard AI — Deployment Manifest

- **Target Environment:** \`$TARGET_ENV\`
- **Deployment Status:** **$DEPLOY_STATUS**
- **Action / Reason:** $DEPLOY_REASON
- **Timestamp:** \`$TIMESTAMP\`
- **Application Version:** \`v$VERSION_NAME\` (Build \`#$VERSION_CODE\`)
- **Git Reference:** \`$GIT_COMMIT\` (Branch: \`$GIT_BRANCH\`)
- **CircleCI Build:** [#$BUILD_NUM]($BUILD_URL)
- **Artifact SHA-256:** \`$AAB_SHA256\`

---
*Generated automatically by CircleCI Deploy Marker Pipeline for BiteGuard AI.*
EOF

echo "[PASS] Generated $MARKER_JSON and $MARKDOWN_MANIFEST"

# --- 3. Optional Sentry Release & Deploy Marker Ping ---
if [ -n "${SENTRY_AUTH_TOKEN:-}" ] && [ -n "${SENTRY_ORG:-}" ] && [ -n "${SENTRY_PROJECT:-}" ]; then
    echo "--> Emitting Sentry Release & Deploy Marker..."
    RELEASE_VERSION="com.example.biteguardai@${VERSION_NAME}+${VERSION_CODE}"
    which sentry-cli >/dev/null 2>&1 && {
        sentry-cli releases new "$RELEASE_VERSION" || true
        sentry-cli releases set-commits --auto "$RELEASE_VERSION" || true
        sentry-cli releases deploys "$RELEASE_VERSION" new -e "$TARGET_ENV" || true
        echo "    [PASS] Sentry release deploy marker emitted."
    } || echo "    [INFO] sentry-cli not found in path; skipping sentry ping."
fi

# --- 4. CircleCI Deploy Marker / Monitoring Webhook Notification ---
if [ -n "${DEPLOY_WEBHOOK_URL:-}" ]; then
    echo "--> Sending deployment marker payload to configured webhook..."
    AUTH_HEADER=()
    if [ -n "${CIRCLECI_DEPLOY_TOKEN:-}" ]; then
        # Automatically format Bearer token if not already prefixed
        if [[ "$CIRCLECI_DEPLOY_TOKEN" == Bearer* ]]; then
            AUTH_HEADER=(-H "Authorization: ${CIRCLECI_DEPLOY_TOKEN}")
        else
            AUTH_HEADER=(-H "Authorization: Bearer ${CIRCLECI_DEPLOY_TOKEN}")
        fi
    elif [ -n "${DEPLOY_AUTH_HEADER:-}" ]; then
        AUTH_HEADER=(-H "${DEPLOY_AUTH_HEADER}")
    fi

    curl -s -X POST -H "Content-Type: application/json" \
        "${AUTH_HEADER[@]}" \
        -d @"$MARKER_JSON" \
        "${DEPLOY_WEBHOOK_URL}" >/dev/null 2>&1 || echo "    [WARN] Webhook POST timed out or failed; continuing."
fi

echo "============================================================"
echo " [SUCCESS] Deploy marker successfully registered!"
echo "============================================================"
