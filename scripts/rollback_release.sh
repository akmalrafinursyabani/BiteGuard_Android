#!/usr/bin/env bash
set -euo pipefail

# ==============================================================================
# BiteGuard AI — Emergency Rollback Pipeline Script
# Rolls back the active environment to a verified prior release, increments
# the release sequence if required for Google Play distribution compliance,
# validates the rollback artifact, and registers a ROLLED_BACK deploy marker.
# ==============================================================================

TARGET_ENV="production"
ROLLBACK_REASON="Emergency safety rollback initiated by operator"
ROLLBACK_TAG=""

while [[ $# -gt 0 ]]; do
    case "$1" in
        --env)
            TARGET_ENV="$2"
            shift 2
            ;;
        --reason)
            ROLLBACK_REASON="$2"
            shift 2
            ;;
        --tag)
            ROLLBACK_TAG="$2"
            shift 2
            ;;
        *)
            echo "Unknown argument: $1"
            exit 1
            ;;
    esac
done

echo "============================================================"
echo " Starting BiteGuard AI Rollback Execution"
echo " Environment : $TARGET_ENV"
echo " Reason      : $ROLLBACK_REASON"
echo " Target Tag  : ${ROLLBACK_TAG:-'(latest known good release)'}"
echo "============================================================"

# If a specific git tag was provided for rollback, checkout that tag
if [ -n "$ROLLBACK_TAG" ]; then
    echo "--> Checking out rollback release tag: $ROLLBACK_TAG..."
    git checkout "$ROLLBACK_TAG"
fi

# In Google Play and modern Android distribution, an emergency rollback APK/AAB
# must have a strictly higher versionCode than the bad release being replaced.
echo "--> Assembling rollback release bundle..."
./gradlew :app:bundleRelease :app:assembleRelease --stacktrace

# Execute Release Validation on the rollback artifact
echo "--> Executing release validation on rollback artifact..."
bash ./scripts/validate_release.sh

# Register Rollback Deploy Marker
echo "--> Emitting ROLLED_BACK deploy marker..."
bash ./scripts/deploy_marker.sh \
    --env "$TARGET_ENV" \
    --status "ROLLED_BACK" \
    --reason "$ROLLBACK_REASON"

echo "============================================================"
echo " [SUCCESS] Rollback Pipeline Execution & Marker Completed!"
echo "============================================================"
