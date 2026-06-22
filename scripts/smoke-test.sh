#!/usr/bin/env bash
set -euo pipefail
# ─────────────────────────────────────────────────────────────────────────────
# smoke-test.sh — verify the DEPLOYED Digital Signature plugin on a Confluence
# instance by exercising the two user-facing paths on a sandbox page:
#   1. RENDERING — the signature macro executes (its export link/panel appears).
#   2. SIGNING   — the /rest/signature/1.0/sign endpoint records a signature
#                  (sign as admin → HTTP 307 → admin then appears in /export).
#
# Usage:
#   smoke-test.sh <host-url> <page-id> [--user U --pass P]
# Default sandbox page id 1383204089 ("Digital Signature Sandbox") if omitted.
# Creds default to $ATLAS_BALOISE_NET_COM_ADMIN_USR / _PWD (admin_b028178 on
# both int and prod).
#
# Exit 0 only if: rendering works, EVERY /sign call returns 307 (REST resource
# alive), AND at least one macro records the admin signature (Bandana write path
# works end-to-end). A macro that returns 307 but doesn't record just means admin
# isn't a signer for that contract (not petition mode) — reported as [ OK ], not a
# failure. Prints a per-macro report.
# ─────────────────────────────────────────────────────────────────────────────

HOST="${1:-}"; PAGE_ID="${2:-1383204089}"; shift $(( $# >= 2 ? 2 : $# )) || true
USER_="${ATLAS_BALOISE_NET_COM_ADMIN_USR:-}"
PASS_="${ATLAS_BALOISE_NET_COM_ADMIN_PWD:-}"
while [ $# -gt 0 ]; do
  case "$1" in
    --user) USER_="$2"; shift 2 ;;
    --pass) PASS_="$2"; shift 2 ;;
    *) echo "smoke-test: unexpected arg $1" >&2; exit 2 ;;
  esac
done
[ -n "$HOST" ] || { echo "usage: smoke-test.sh <host-url> [page-id] [--user U --pass P]" >&2; exit 2; }
[ -n "$USER_" ] && [ -n "$PASS_" ] || { echo "smoke-test: missing creds (ATLAS_BALOISE_NET_COM_ADMIN_USR/PWD or --user/--pass)" >&2; exit 2; }

HOST="${HOST%/}" PAGE_ID="$PAGE_ID" SMOKE_USER="$USER_" SMOKE_PASS="$PASS_" python3 <<'PY'
import json, os, re, subprocess, sys

HOST = os.environ["HOST"]; PAGE = os.environ["PAGE_ID"]
USER = os.environ["SMOKE_USER"]; PW = os.environ["SMOKE_PASS"]

def curl(url, *extra):
    p = subprocess.run(["curl", "-s", "-w", "\n%{http_code}", "--user", f"{USER}:{PW}", *extra, url],
                       capture_output=True, text=True)
    body, _, code = p.stdout.rpartition("\n")
    return code, body

def fail(msg):
    print(f"\033[1;31m[FAIL]\033[0m {msg}"); sys.exit(1)

print(f"==> Smoke test: {HOST}  page {PAGE}  as {USER}")

# admin display name (for the recording check)
code, body = curl(f"{HOST}/rest/api/user?username={USER}")
admin_name = ""
try: admin_name = json.loads(body).get("displayName", "")
except Exception: pass
if not admin_name:
    print(f"  [warn] could not resolve admin displayName (HTTP {code}); recording check will rely on the export not erroring")

# 1. RENDERING
code, body = curl(f"{HOST}/rest/api/content/{PAGE}?expand=body.view", "-H", "Accept: application/json")
if code != "200":
    fail(f"page fetch HTTP {code} (auth? page id? plugin installed?)")
try:
    view = json.loads(body)["body"]["view"]["value"]
except Exception:
    fail(f"could not read body.view from page response: {body[:300]}")
keys = sorted(set(re.findall(r"/rest/signature/1\.0/export\?key=(signature\.[0-9a-f]+)", view)))
if not keys:
    fail("no signature macro rendered on the page (export link absent) — macro not executing / plugin broken")
if "conf-macro" in view and "data-macro-name=\"signature\"" in view and "Unknown macro" in view:
    fail("page contains an 'Unknown macro' placeholder — plugin not providing the macro")
print(f"  \033[1;32m[PASS]\033[0m rendering: {len(keys)} signature macro(s) executed")

# 2. SIGNING (+ recording) per macro.
# Pass criteria: every sign endpoint responds 307 (REST resource alive) AND at least one
# macro records the admin signature (Bandana write path works end-to-end). A 307 that does
# NOT record just means admin isn't a signer for that contract (e.g. not petition mode) —
# that's sandbox config, not a deploy defect.
all_signed = True
recorded_any = False
for key in keys:
    sc, _ = curl(f"{HOST}/rest/signature/1.0/sign?key={key}")   # do NOT follow the 307
    signed_ok = (sc == "307")
    ec, ehtml = curl(f"{HOST}/rest/signature/1.0/export?key={key}", "-H", "Accept: text/html")
    recorded = (ec == "200" and not ehtml.lstrip().startswith("ERROR")
                and (admin_name in ehtml if admin_name else True))
    if signed_ok and recorded:
        tag, detail = "\033[1;32m[PASS]\033[0m", "sign 307; signature recorded"
    elif signed_ok:
        tag, detail = "\033[1;33m[ OK ]\033[0m", "sign 307; admin is not a signer for this contract (not recorded) — sandbox config"
    else:
        tag, detail = "\033[1;31m[FAIL]\033[0m", f"sign HTTP {sc} (expected 307) — REST resource broken"
    print(f"  {tag} {key[:24]}…  {detail}")
    all_signed = all_signed and signed_ok
    recorded_any = recorded_any or recorded

if not all_signed:
    fail("a /sign call did not return 307 — the signature REST resource is broken")
if not recorded_any:
    fail("no macro recorded the admin signature — Bandana write path broken (or admin signs no macro on this page)")
print(f"\033[1;32m==> SMOKE PASS\033[0m  {HOST} page {PAGE}  (rendering + signing verified)")
PY
