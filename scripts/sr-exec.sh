#!/usr/bin/env bash
set -euo pipefail
# ─────────────────────────────────────────────────────────────────────────────
# sr-exec.sh — run a Groovy script on a Confluence DC ScriptRunner instance,
# headlessly, as admin (no browser session needed).
#
# Endpoint:  POST <host>/rest/scriptrunner/latest/user/exec/
#            headers: Content-Type: application/json, X-Atlassian-token: no-check
#            body:    {"script": "<groovy>", "scriptPath": null, "parameters": {}}
#
# Usage:
#   sr-exec.sh [opts] path/to/script.groovy     # from a file
#   sr-exec.sh [opts] -e 'return "hi"'           # inline
#   echo 'return ...' | sr-exec.sh [opts] -      # from stdin
#
# Options (all optional):
#   --host URL   default: $CONFLUENCE_URL or https://confluence.baloisenet.com
#   --user U     default: $SR_EXEC_USER or $ATLAS_BALOISE_NET_COM_ADMIN_USR
#   --pass P     default: $SR_EXEC_PASS or $ATLAS_BALOISE_NET_COM_ADMIN_PWD
#
# Prints the Groovy `output` on stdout. Exits non-zero (printing the exception /
# HTTP error to stderr) on any failure — safe to use in `set -e` pipelines.
#
# Groovy tips (Confluence DC):
#   import com.atlassian.sal.api.component.ComponentLocator
#   def bm = ComponentLocator.getComponent(com.atlassian.bandana.BandanaManager)
#   def pm = ComponentLocator.getComponent(com.atlassian.confluence.pages.PageManager)
#   return <serializable value or string>   // becomes the `output`
# ─────────────────────────────────────────────────────────────────────────────

HOST="${CONFLUENCE_URL:-https://confluence.baloisenet.com}"
USER_="${SR_EXEC_USER:-${ATLAS_BALOISE_NET_COM_ADMIN_USR:-}}"
PASS_="${SR_EXEC_PASS:-${ATLAS_BALOISE_NET_COM_ADMIN_PWD:-}}"
INLINE="" ; SRC=""

while [ $# -gt 0 ]; do
  case "$1" in
    --host) HOST="$2"; shift 2 ;;
    --user) USER_="$2"; shift 2 ;;
    --pass) PASS_="$2"; shift 2 ;;
    -e)     INLINE="$2"; shift 2 ;;
    -)      SRC="-"; shift ;;
    -h|--help) sed -n '2,40p' "$0" | sed 's/^# \{0,1\}//'; exit 0 ;;
    *)      SRC="$1"; shift ;;
  esac
done

[ -n "$USER_" ] && [ -n "$PASS_" ] || { echo "sr-exec: missing creds (ATLAS_BALOISE_NET_COM_ADMIN_USR/PWD or --user/--pass)" >&2; exit 2; }

if   [ -n "$INLINE" ];                 then GROOVY="$INLINE"
elif [ "$SRC" = "-" ];                 then GROOVY="$(cat)"
elif [ -n "$SRC" ] && [ -f "$SRC" ];   then GROOVY="$(cat "$SRC")"
else echo "sr-exec: no script given (file path, -e '<groovy>', or - for stdin)" >&2; exit 2; fi

# Log the target (stderr — keeps stdout clean for the Groovy output).
printf '\033[1;34m==>\033[0m ScriptRunner exec @ %s (as %s)\n' "$HOST" "$USER_" >&2

GROOVY="$GROOVY" python3 - "$HOST" "$USER_" "$PASS_" <<'PY'
import json, os, subprocess, sys
host, user, pw = sys.argv[1], sys.argv[2], sys.argv[3]
payload = json.dumps({"script": os.environ["GROOVY"], "scriptPath": None, "parameters": {}})
url = host.rstrip("/") + "/rest/scriptrunner/latest/user/exec/"
p = subprocess.run(
    ["curl", "-s", "-w", "\n%{http_code}", "--user", f"{user}:{pw}",
     "-H", "Content-Type: application/json", "-H", "Accept: application/json",
     "-H", "X-Atlassian-token: no-check", "-X", "POST", "--data-raw", payload, url],
    capture_output=True, text=True)
if p.returncode != 0:
    sys.stderr.write(f"sr-exec: curl failed ({p.returncode}): {p.stderr.strip()}\n"); sys.exit(1)
body, _, code = p.stdout.rpartition("\n")
if code != "200":
    sys.stderr.write(f"sr-exec: HTTP {code}: {body[:1000]}\n"); sys.exit(1)
try:
    d = json.loads(body)
except Exception:
    sys.stderr.write(f"sr-exec: non-JSON response: {body[:1000]}\n"); sys.exit(1)
exc = (d.get("snapshot") or {}).get("exception")
if exc:
    sys.stderr.write(f"sr-exec: Groovy exception: {exc}\n"); sys.exit(1)
out = d.get("output")
if out is not None:
    print(out)
PY
