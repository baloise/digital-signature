#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
JAR_PATH="$PROJECT_DIR/target/digital-signature-9.0.1.jar"
PLUGIN_KEY="com.baloise.confluence.digital-signature"
BASE_URL="http://localhost:8090"
ADMIN_USER="admin"
ADMIN_PASS="admin"

usage() {
    cat <<EOF
Usage: $0 <command> <confluence-version>

Commands:
  start     Create pod with PostgreSQL + Confluence at the given version
  logs      Tail Confluence logs
  upload    Upload plugin JAR via UPM REST API
  verify    Check plugin is installed and enabled
  teardown  Remove pod, containers, and volumes

Examples:
  $0 start 9.3.2
  $0 upload 9.3.2
  $0 verify 9.3.2
  $0 teardown 9.3.2
EOF
    exit 1
}

[ $# -ge 2 ] || usage

COMMAND="$1"
CONFLUENCE_VERSION="$2"

POD_NAME="confluence-test-${CONFLUENCE_VERSION}"
POSTGRES_VOL="postgres-data-${CONFLUENCE_VERSION}"
CONFLUENCE_VOL="confluence-data-${CONFLUENCE_VERSION}"
CONTAINER_POSTGRES="postgres-${CONFLUENCE_VERSION}"
CONTAINER_CONFLUENCE="confluence-${CONFLUENCE_VERSION}"

cmd_start() {
    echo "==> Starting Confluence ${CONFLUENCE_VERSION} test environment"

    # Clean up any previous run
    podman pod rm -f "$POD_NAME" 2>/dev/null || true
    podman volume rm "$POSTGRES_VOL" "$CONFLUENCE_VOL" 2>/dev/null || true

    # Create volumes
    podman volume create "$POSTGRES_VOL"
    podman volume create "$CONFLUENCE_VOL"

    # Create pod with port mappings
    podman pod create --name "$POD_NAME" \
        -p 8090:8090 \
        -p 8091:8091 \
        -p 5005:5005

    # Start PostgreSQL
    podman run --name "$CONTAINER_POSTGRES" \
        --pod "$POD_NAME" \
        -v "${POSTGRES_VOL}:/var/lib/postgresql/data" \
        -e POSTGRES_PASSWORD=mysecretpassword \
        -d postgres

    # Start Confluence
    local jvm_args="-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=*:5005"
    jvm_args="$jvm_args -Datlassian.upm.signature.check.disabled=true"
    jvm_args="$jvm_args -Dupm.plugin.upload.enabled=true"

    podman run --name "$CONTAINER_CONFLUENCE" \
        --pod "$POD_NAME" \
        -v "${CONFLUENCE_VOL}:/var/atlassian/application-data/confluence" \
        -d \
        -e JVM_MINIMUM_MEMORY=1536m \
        -e JVM_MAXIMUM_MEMORY=1536m \
        -e JVM_SUPPORT_RECOMMENDED_ARGS="$jvm_args" \
        "atlassian/confluence:${CONFLUENCE_VERSION}"

    echo "==> Waiting for Confluence to be ready..."
    local max_attempts=60
    local attempt=0
    while [ $attempt -lt $max_attempts ]; do
        local http_code
        http_code=$(curl -s -o /dev/null -w "%{http_code}" "$BASE_URL/status" 2>/dev/null || echo "000")
        if [ "$http_code" = "200" ] || [ "$http_code" = "302" ]; then
            echo "==> Confluence ${CONFLUENCE_VERSION} is ready at ${BASE_URL}"
            return 0
        fi
        attempt=$((attempt + 1))
        printf "    Waiting... (%d/%d)\r" "$attempt" "$max_attempts"
        sleep 5
    done
    echo ""
    echo "==> WARNING: Confluence did not respond within 5 minutes. Check logs:"
    echo "    $0 logs ${CONFLUENCE_VERSION}"
    return 1
}

cmd_logs() {
    podman logs -f "$CONTAINER_CONFLUENCE"
}

cmd_upload() {
    if [ ! -f "$JAR_PATH" ]; then
        echo "==> Plugin JAR not found at $JAR_PATH"
        echo "    Run: mvn package -DskipTests"
        exit 1
    fi

    echo "==> Uploading plugin to Confluence ${CONFLUENCE_VERSION}..."

    # Get UPM token
    local upm_token
    upm_token=$(curl -s -I --user "${ADMIN_USER}:${ADMIN_PASS}" \
        -H 'Accept: application/vnd.atl.plugins.installed+json' \
        "${BASE_URL}/rest/plugins/1.0/?os_authType=basic" 2>/dev/null \
        | grep -i 'upm-token' | awk '{print $2}' | tr -d '\r\n')

    if [ -z "$upm_token" ]; then
        echo "==> ERROR: Could not get UPM token. Is Confluence running and setup complete?"
        exit 1
    fi

    # Upload plugin
    local response
    response=$(curl -s --user "${ADMIN_USER}:${ADMIN_PASS}" \
        -H 'Accept: application/json' \
        "${BASE_URL}/rest/plugins/1.0/?token=${upm_token}" \
        -F "plugin=@${JAR_PATH}")

    echo "==> Upload response:"
    echo "$response" | python3 -m json.tool 2>/dev/null || echo "$response"
}

cmd_verify() {
    echo "==> Verifying plugin on Confluence ${CONFLUENCE_VERSION}..."

    local response
    response=$(curl -s --user "${ADMIN_USER}:${ADMIN_PASS}" \
        -H 'Accept: application/vnd.atl.plugins.plugin+json' \
        "${BASE_URL}/rest/plugins/1.0/${PLUGIN_KEY}-key" 2>/dev/null)

    local enabled
    enabled=$(echo "$response" | python3 -c "import sys,json; print(json.load(sys.stdin).get('enabled', False))" 2>/dev/null || echo "PARSE_ERROR")

    if [ "$enabled" = "True" ]; then
        echo "==> PASS: Plugin '${PLUGIN_KEY}' is installed and enabled on Confluence ${CONFLUENCE_VERSION}"
        return 0
    else
        echo "==> FAIL: Plugin is not enabled on Confluence ${CONFLUENCE_VERSION}"
        echo "    Response: $response"
        return 1
    fi
}

cmd_teardown() {
    echo "==> Tearing down Confluence ${CONFLUENCE_VERSION} test environment..."
    podman pod rm -f "$POD_NAME" 2>/dev/null || true
    podman volume rm "$POSTGRES_VOL" "$CONFLUENCE_VOL" 2>/dev/null || true
    echo "==> Teardown complete."
}

case "$COMMAND" in
    start)    cmd_start ;;
    logs)     cmd_logs ;;
    upload)   cmd_upload ;;
    verify)   cmd_verify ;;
    teardown) cmd_teardown ;;
    *)        usage ;;
esac
