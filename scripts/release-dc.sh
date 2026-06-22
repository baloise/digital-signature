#!/usr/bin/env bash
set -euo pipefail
# ─────────────────────────────────────────────────────────────────────────────
# release-dc.sh — build / upload / verify the Digital Signature DC plugin on a
# real Confluence instance via the UPM REST API.
#
# Subcommands:
#   test                      Run the unit tests (mvn clean test).
#   build                     Build the v9 plugin JAR (mvn clean package -DskipTests)
#                             and stage it under staging/.
#   jar                       Print the resolved JAR path that upload would use.
#   upload <host-url>         Upload the staged JAR to <host-url> via UPM, then verify.
#   verify <host-url>         Report installed version + enabled state on <host-url>.
#
# Hosts (same admin_b028178 on both):
#   https://int-confluence.baloisenet.com   (integration — smoke-test first)
#   https://confluence.baloisenet.com        (production)
#
# Credentials default to $ATLAS_BALOISE_NET_COM_ADMIN_USR / _PWD; override with
# --user / --pass. UPM upload requires a Confluence system administrator.
#
# This script does NOT chain int->prod automatically. The release SKILL drives
# the gated sequence (build -> upload int -> smoke int -> upload prod -> smoke prod)
# so a human/agent confirms the int smoke test before prod.
# ─────────────────────────────────────────────────────────────────────────────

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
PLUGIN_KEY="com.baloise.confluence.digital-signature"

USER_="${ATLAS_BALOISE_NET_COM_ADMIN_USR:-}"
PASS_="${ATLAS_BALOISE_NET_COM_ADMIN_PWD:-}"

log()  { printf '\033[1;34m==>\033[0m %s\n' "$*"; }
warn() { printf '\033[1;33m[warn]\033[0m %s\n' "$*" >&2; }
die()  { printf '\033[1;31m[FAIL]\033[0m %s\n' "$*" >&2; exit 1; }

# Pull --user/--pass out of the args, leave the rest in REST_ARGS.
HOST=""
parse_host_and_creds() {
  HOST=""
  while [ $# -gt 0 ]; do
    case "$1" in
      --user) USER_="$2"; shift 2 ;;
      --pass) PASS_="$2"; shift 2 ;;
      http*)  HOST="$1"; shift ;;
      *)      die "unexpected argument: $1" ;;
    esac
  done
  [ -n "$HOST" ] || die "missing <host-url>"
  HOST="${HOST%/}"
  [ -n "$USER_" ] && [ -n "$PASS_" ] || die "missing creds (set ATLAS_BALOISE_NET_COM_ADMIN_USR/PWD or pass --user/--pass)"
}

resolve_jar() {
  JAR_PATH=""
  local newest="" newest_ts=0 f ts
  for f in "$PROJECT_DIR"/staging/digital-signature-9.*.jar "$PROJECT_DIR"/target/digital-signature-*.jar; do
    [ -f "$f" ] || continue
    case "$f" in *-tests.jar) continue ;; esac
    ts=$(stat -c %Y "$f" 2>/dev/null || echo 0)
    if [ "$ts" -gt "$newest_ts" ]; then newest_ts="$ts"; newest="$f"; fi
  done
  JAR_PATH="$newest"
}

plugin_version() {  # read <version> from the pom
  mvn -q help:evaluate -Dexpression=project.version -DforceStdout --file "$PROJECT_DIR/pom.xml" 2>/dev/null
}

cmd_test()  { log "Unit tests (mvn clean test)"; mvn -B clean test --file "$PROJECT_DIR/pom.xml"; }

cmd_build() {
  log "Building v9 plugin JAR (mvn clean package -DskipTests)"
  mvn -B clean package -DskipTests --file "$PROJECT_DIR/pom.xml"
  mkdir -p "$PROJECT_DIR/staging"
  local f
  for f in "$PROJECT_DIR"/target/digital-signature-*.jar; do
    case "$f" in *-tests.jar) continue ;; esac
    cp "$f" "$PROJECT_DIR/staging/"
  done
  resolve_jar
  [ -n "$JAR_PATH" ] || die "no JAR produced"
  log "Built + staged: $JAR_PATH (pom version $(plugin_version))"
}

cmd_jar() { resolve_jar; [ -n "$JAR_PATH" ] || die "no staged JAR — run: $0 build"; echo "$JAR_PATH"; }

upm_verify() {  # $1 host ; prints version, returns 0 if enabled
  local resp enabled version
  resp=$(curl -s --user "${USER_}:${PASS_}" -H 'Accept: application/vnd.atl.plugins.plugin+json' \
         "${1}/rest/plugins/1.0/${PLUGIN_KEY}-key")
  enabled=$(echo "$resp" | python3 -c "import sys,json;print(json.load(sys.stdin).get('enabled',False))" 2>/dev/null || echo "PARSE_ERROR")
  version=$(echo "$resp" | python3 -c "import sys,json;print(json.load(sys.stdin).get('version','?'))" 2>/dev/null || echo "?")
  echo "    installed version: ${version}  enabled: ${enabled}"
  [ "$enabled" = "True" ]
}

cmd_verify() { parse_host_and_creds "$@"; log "Verifying plugin on ${HOST}"; upm_verify "$HOST" && log "PASS: enabled on ${HOST}" || die "plugin not enabled on ${HOST}"; }

cmd_upload() {
  parse_host_and_creds "$@"
  resolve_jar
  [ -n "$JAR_PATH" ] && [ -f "$JAR_PATH" ] || die "no staged JAR — run: $0 build"
  log "Uploading $(basename "$JAR_PATH") -> ${HOST}"

  local token
  token=$(curl -s -I --user "${USER_}:${PASS_}" -H 'Accept: application/vnd.atl.plugins.installed+json' \
          "${HOST}/rest/plugins/1.0/?os_authType=basic" | grep -i '^upm-token:' | awk '{print $2}' | tr -d '\r\n')
  [ -n "$token" ] || die "could not obtain UPM token from ${HOST} (sysadmin rights / basic auth required)"

  local resp
  resp=$(curl -s --user "${USER_}:${PASS_}" -H 'Accept: application/json' \
         "${HOST}/rest/plugins/1.0/?token=${token}" -F "plugin=@${JAR_PATH}")
  echo "$resp" | python3 -m json.tool 2>/dev/null || echo "$resp"

  # UPM installs asynchronously and the enabled flag can briefly flap while
  # spring-scanner modules come up — require two consecutive enabled reads.
  log "Waiting for the plugin to reach a stable enabled state on ${HOST}..."
  local i ok=0
  for i in $(seq 1 30); do
    if upm_verify "$HOST" >/dev/null 2>&1; then ok=$((ok + 1)); else ok=0; fi
    [ "$ok" -ge 2 ] && break
    sleep 3
  done
  log "Final state on ${HOST}:"; upm_verify "$HOST" || true
  [ "$ok" -ge 2 ] && { log "PASS: enabled on ${HOST}"; return 0; }
  die "plugin did not reach a stable enabled state on ${HOST} within 90s"
}

CMD="${1:-}"; shift || true
case "$CMD" in
  test)   cmd_test ;;
  build)  cmd_build ;;
  jar)    cmd_jar ;;
  upload) cmd_upload "$@" ;;
  verify) cmd_verify "$@" ;;
  *) die "usage: $0 {test|build|jar|upload <host-url>|verify <host-url>} [--user U --pass P]" ;;
esac
