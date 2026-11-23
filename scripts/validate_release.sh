#!/usr/bin/env bash
set -euo pipefail

# ==============================================================================
# BiteGuard AI — Automated Release Validation Gate
# Validates release artifact integrity, R8 mapping files, manifest security,
# size budgets, and secret leakage prevention before any deployment.
# ==============================================================================

echo "============================================================"
echo " Starting BiteGuard AI Release Validation Gate"
echo "============================================================"

AAB_PATH="app/build/outputs/bundle/release/app-release.aab"
APK_PATH="app/build/outputs/apk/release/app-release-unsigned.apk"
if [ ! -f "$APK_PATH" ]; then
    APK_PATH="app/build/outputs/apk/release/app-release.apk"
fi
MAPPING_DIR="app/build/outputs/mapping/release"
MERGED_MANIFEST="app/build/intermediates/merged_manifest/release/processReleaseMainManifest/AndroidManifest.xml"
if [ ! -f "$MERGED_MANIFEST" ]; then
    MERGED_MANIFEST="app/build/intermediates/merged_manifests/release/processReleaseManifest/AndroidManifest.xml"
fi
OUTPUT_DIR="build/reports/release-validation"
mkdir -p "$OUTPUT_DIR"

VALIDATION_FAILED=0

# --- Gate 1: Artifact Existence & Size Budget ---
echo "--> Gate 1: Checking release artifact existence and size budget (< 50MB)..."
if [ -f "$AAB_PATH" ]; then
    AAB_SIZE=$(wc -c < "$AAB_PATH" | tr -d ' ')
    AAB_SIZE_MB=$((AAB_SIZE / 1048576))
    echo "    [PASS] Release AAB found at $AAB_PATH (${AAB_SIZE_MB}MB / ${AAB_SIZE} bytes)"
    if [ "$AAB_SIZE_MB" -gt 50 ]; then
        echo "    [FAIL] Release AAB exceeds 50MB size budget (${AAB_SIZE_MB}MB)!"
        VALIDATION_FAILED=1
    fi
else
    echo "    [FAIL] Release AAB not found at $AAB_PATH!"
    VALIDATION_FAILED=1
fi

if [ -f "$APK_PATH" ]; then
    APK_SIZE=$(wc -c < "$APK_PATH" | tr -d ' ')
    APK_SIZE_MB=$((APK_SIZE / 1048576))
    echo "    [PASS] Release APK found at $APK_PATH (${APK_SIZE_MB}MB / ${APK_SIZE} bytes)"
else
    echo "    [WARN] Release APK not found at $APK_PATH (AAB will be primary deployment artifact)"
fi

# --- Gate 2: ProGuard / R8 Obfuscation & Mapping Files ---
echo "--> Gate 2: Verifying R8 code shrinking and mapping artifacts..."
if [ -d "$MAPPING_DIR" ]; then
    echo "    [PASS] R8 mapping output directory found at $MAPPING_DIR"
    ls -la "$MAPPING_DIR"
else
    echo "    [FAIL] R8 mapping directory missing! Release build must have R8 optimization enabled."
    VALIDATION_FAILED=1
fi

# --- Gate 3: Manifest Security & Feature Integrity ---
echo "--> Gate 3: Auditing merged release AndroidManifest.xml..."
if [ -f "$MERGED_MANIFEST" ]; then
    # Ensure android:debuggable is NOT set to true
    if grep -q 'android:debuggable="true"' "$MERGED_MANIFEST"; then
        echo "    [FAIL] SECURITY RISK: android:debuggable=\"true\" detected in release manifest!"
        VALIDATION_FAILED=1
    else
        echo "    [PASS] android:debuggable is false / disabled."
    fi

    # Verify required Camera and Internet permissions (SRS FR-02 & FR-03)
    if grep -q 'android.permission.CAMERA' "$MERGED_MANIFEST" && grep -q 'android.permission.INTERNET' "$MERGED_MANIFEST"; then
        echo "    [PASS] Required CAMERA and INTERNET permissions verified in manifest."
    else
        echo "    [FAIL] Missing required CAMERA or INTERNET permissions in manifest!"
        VALIDATION_FAILED=1
    fi
else
    echo "    [WARN] Merged manifest not found for static inspection; skipping manifest text check."
fi

# --- Gate 4: Secret Leakage Prevention ---
echo "--> Gate 4: Auditing for unencrypted hardcoded secret keys in app source code..."
LEAK_FOUND=0
# Scan app/core/feature source code excluding scripts, test mocks, and build outputs
SEARCH_TARGETS="app/src core feature"
if grep -rn --exclude="*.keystore" "AIzaSy[A-Za-z0-9_-]\{33\}" $SEARCH_TARGETS > "$OUTPUT_DIR/leaked_keys.txt" 2>/dev/null; then
    if [ -s "$OUTPUT_DIR/leaked_keys.txt" ]; then
        echo "    [FAIL] Potential exposed Google/Gemini API key pattern found in source code!"
        cat "$OUTPUT_DIR/leaked_keys.txt"
        LEAK_FOUND=1
        VALIDATION_FAILED=1
    fi
fi
if [ "$LEAK_FOUND" -eq 0 ]; then
    echo "    [PASS] No hardcoded production API keys detected in git tracked source."
fi

# --- Gate 5: Artifact Checksum Generation ---
echo "--> Gate 5: Computing release artifact SHA-256 checksums..."
if [ -f "$AAB_PATH" ]; then
    sha256sum "$AAB_PATH" | tee "$OUTPUT_DIR/aab_sha256.txt"
fi
if [ -f "$APK_PATH" ]; then
    sha256sum "$APK_PATH" | tee "$OUTPUT_DIR/apk_sha256.txt"
fi

# --- Summary Report Generation ---
cat <<EOF > "$OUTPUT_DIR/release_validation_report.json"
{
  "timestamp": "$(date -u +"%Y-%m-%dT%H:%M:%SZ")",
  "validation_passed": $([ "$VALIDATION_FAILED" -eq 0 ] && echo "true" || echo "false"),
  "aab_path": "$AAB_PATH",
  "aab_exists": $([ -f "$AAB_PATH" ] && echo "true" || echo "false"),
  "apk_path": "$APK_PATH",
  "apk_exists": $([ -f "$APK_PATH" ] && echo "true" || echo "false"),
  "mapping_dir": "$MAPPING_DIR",
  "r8_optimized": $([ -d "$MAPPING_DIR" ] && echo "true" || echo "false")
}
EOF

echo "============================================================"
if [ "$VALIDATION_FAILED" -eq 0 ]; then
    echo " [SUCCESS] All Release Validation Gates Passed Cleanly!"
    echo "============================================================"
    exit 0
else
    echo " [FAILURE] Release Validation Gate Encountered Errors!"
    echo "============================================================"
    exit 1
fi
