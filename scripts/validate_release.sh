#!/usr/bin/env bash
set -euo pipefail

REPORT_DIR="build/reports/release-validation"
REPORT_FILE="${REPORT_DIR}/validation-report.txt"

mkdir -p "$REPORT_DIR"

echo "=== BiteGuard Release Validation Gate ===" | tee "$REPORT_FILE"
echo "Timestamp: $(date -u '+%Y-%m-%dT%H:%M:%SZ')" | tee -a "$REPORT_FILE"
echo "" | tee -a "$REPORT_FILE"

PASS=true

check_artifact() {
  local label="$1"
  local path="$2"
  if [ -f "$path" ]; then
    echo "[PASS] $label: $path" | tee -a "$REPORT_FILE"
  else
    echo "[FAIL] $label not found: $path" | tee -a "$REPORT_FILE"
    PASS=false
  fi
}

check_artifact "Release AAB"  "app/build/outputs/bundle/release/app-release.aab"
check_artifact "Release APK"  "app/build/outputs/apk/release/app-release.apk"
check_artifact "R8 mapping"   "app/build/outputs/mapping/release/mapping.txt"

echo "" | tee -a "$REPORT_FILE"
if [ "$PASS" = "true" ]; then
  echo "Result: PASSED" | tee -a "$REPORT_FILE"
  exit 0
else
  echo "Result: FAILED — one or more artifacts are missing" | tee -a "$REPORT_FILE"
  exit 1
fi
