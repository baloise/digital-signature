#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
PLUGIN_KEY="com.baloise.confluence.digital-signature"
ADMIN_USER="admin"
ADMIN_PASS="admin"

usage() {
    cat <<EOF
Usage: $0 <command> <confluence-version>

Commands:
  build     Build the plugin JAR and copy it to staging/
  start     Create docker network with PostgreSQL + Confluence at the given version
  logs      Tail Confluence logs
  upload    Upload the newest JAR from staging/ matching the major version
  verify    Check plugin is installed and enabled
  teardown  Remove containers, network, and volumes

The upload command picks the most recently built JAR from staging/ that
matches the Confluence major version (e.g. staging/digital-signature-9.*.jar).
Run 'build' first to populate staging/.

Ports are derived from the major version (V9 -> 9090, V10 -> 10090):
  V9:  HTTP 9090,  sync 9091,  debug 9005
  V10: HTTP 10090, sync 10091, debug 10005

During Confluence setup, use JDBC URL:
  jdbc:postgresql://<postgres-container>:5432/postgres
  e.g. jdbc:postgresql://postgres-9.5.4:5432/postgres

Examples:
  $0 build 9.5.4        # Build v9 plugin -> staging/
  $0 build 10.2.7       # Build v10 plugin -> staging/
  $0 start 9.5.4 &      # Start on port 9090 (background)
  $0 start 10.2.7 &     # Start on port 10090 (background)
  $0 upload 9.5.4       # Upload newest staging/digital-signature-9.*.jar
  $0 upload 10.2.7      # Upload newest staging/digital-signature-10.*.jar
EOF
    exit 1
}

[ $# -ge 2 ] || usage

COMMAND="$1"
CONFLUENCE_VERSION="$2"

CONFLUENCE_MAJOR="${CONFLUENCE_VERSION%%.*}"
HOST_PORT=$((CONFLUENCE_MAJOR * 1000 + 90))
SYNC_PORT=$((CONFLUENCE_MAJOR * 1000 + 91))
DEBUG_PORT=$((CONFLUENCE_MAJOR * 1000 + 5))
BASE_URL="http://localhost:${HOST_PORT}"

NETWORK_NAME="ds-net-${CONFLUENCE_VERSION}"
POSTGRES_VOL="ds-pgdata-${CONFLUENCE_VERSION}"
CONFLUENCE_VOL="ds-confdata-${CONFLUENCE_VERSION}"
CONTAINER_POSTGRES="ds-postgres-${CONFLUENCE_VERSION}"
CONTAINER_CONFLUENCE="ds-confluence-${CONFLUENCE_VERSION}"

resolve_jar() {
    JAR_PATH=""
    local newest=""
    local newest_ts=0
    for f in "$PROJECT_DIR"/staging/digital-signature-"${CONFLUENCE_MAJOR}".*.jar; do
        [ -f "$f" ] || continue
        local ts
        ts=$(stat -f %m "$f" 2>/dev/null || stat -c %Y "$f" 2>/dev/null || echo 0)
        if [ "$ts" -gt "$newest_ts" ]; then
            newest_ts="$ts"
            newest="$f"
        fi
    done
    JAR_PATH="$newest"
}

cmd_build() {
    echo "==> Building plugin for Confluence ${CONFLUENCE_MAJOR}"
    local c9_version
    c9_version=$(mvn -q help:evaluate -Dexpression=project.version -DforceStdout --file "$PROJECT_DIR/pom.xml")

    if [ "$CONFLUENCE_MAJOR" = "9" ]; then
        mvn -B clean package -DskipTests --file "$PROJECT_DIR/pom.xml"
    elif [ "$CONFLUENCE_MAJOR" = "10" ]; then
        local c10_version="10.${c9_version#*.}"
        echo "==> Setting version to ${c10_version} for Confluence 10 build"
        mvn -B versions:set -DnewVersion="$c10_version" -DgenerateBackupPoms=false --file "$PROJECT_DIR/pom.xml"
        mvn -B clean package -Pconfluence10 -DskipTests --file "$PROJECT_DIR/pom.xml"
        mvn -B versions:set -DnewVersion="$c9_version" -DgenerateBackupPoms=false --file "$PROJECT_DIR/pom.xml"
    else
        echo "==> ERROR: Unsupported major version: ${CONFLUENCE_MAJOR}"
        exit 1
    fi

    mkdir -p "$PROJECT_DIR/staging"
    for f in "$PROJECT_DIR"/target/digital-signature-*.jar; do
        case "$f" in *-tests.jar) continue ;; esac
        cp "$f" "$PROJECT_DIR/staging/"
    done
    resolve_jar
    echo "==> Built: $JAR_PATH"
}

cmd_start() {
    echo "==> Starting Confluence ${CONFLUENCE_VERSION} test environment"

    # Clean up any previous run
    docker rm -f "$CONTAINER_CONFLUENCE" "$CONTAINER_POSTGRES" 2>/dev/null || true
    docker network rm "$NETWORK_NAME" 2>/dev/null || true
    docker volume rm "$POSTGRES_VOL" "$CONFLUENCE_VOL" 2>/dev/null || true

    # Create volumes and network
    docker volume create "$POSTGRES_VOL"
    docker volume create "$CONFLUENCE_VOL"
    docker network create "$NETWORK_NAME"

    # Start PostgreSQL
    docker run --name "$CONTAINER_POSTGRES" \
        --network "$NETWORK_NAME" \
        -v "${POSTGRES_VOL}:/var/lib/postgresql/data" \
        -e POSTGRES_PASSWORD=mysecretpassword \
        -d postgres:17

    # Start Confluence
    local jvm_args="-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=*:5005"
    jvm_args="$jvm_args -Datlassian.upm.signature.check.disabled=true"
    jvm_args="$jvm_args -Dupm.plugin.upload.enabled=true"

    docker run --name "$CONTAINER_CONFLUENCE" \
        --network "$NETWORK_NAME" \
        -v "${CONFLUENCE_VOL}:/var/atlassian/application-data/confluence" \
        -p "${HOST_PORT}:8090" \
        -p "${SYNC_PORT}:8091" \
        -p "${DEBUG_PORT}:5005" \
        -d \
        -e JVM_MINIMUM_MEMORY=1536m \
        -e JVM_MAXIMUM_MEMORY=1536m \
        -e JVM_SUPPORT_RECOMMENDED_ARGS="$jvm_args" \
        "atlassian/confluence:${CONFLUENCE_VERSION}"

    echo "==> Waiting for Confluence to be ready..."
    echo "    JDBC URL for setup: jdbc:postgresql://${CONTAINER_POSTGRES}:5432/postgres"
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
    docker logs -f "$CONTAINER_CONFLUENCE"
}

cmd_upload() {
    resolve_jar
    if [ -z "$JAR_PATH" ] || [ ! -f "$JAR_PATH" ]; then
        echo "==> Plugin JAR not found for Confluence ${CONFLUENCE_MAJOR}"
        echo "    Run: $0 build ${CONFLUENCE_VERSION}"
        exit 1
    fi

    echo "==> Uploading $JAR_PATH to Confluence ${CONFLUENCE_VERSION} at ${BASE_URL}..."

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
    echo "==> Verifying plugin on Confluence ${CONFLUENCE_VERSION} at ${BASE_URL}..."

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
    docker rm -f "$CONTAINER_CONFLUENCE" "$CONTAINER_POSTGRES" 2>/dev/null || true
    docker network rm "$NETWORK_NAME" 2>/dev/null || true
    docker volume rm "$POSTGRES_VOL" "$CONFLUENCE_VOL" 2>/dev/null || true
    echo "==> Teardown complete."
}

case "$COMMAND" in
    build)    cmd_build ;;
    start)    cmd_start ;;
    logs)     cmd_logs ;;
    upload)   cmd_upload ;;
    verify)   cmd_verify ;;
    teardown) cmd_teardown ;;
    *)        usage ;;
esac
