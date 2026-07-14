#!/usr/bin/env bash
set -euo pipefail
trap 'printf "\033[1;31m[FAIL]\033[0m add-editor-to-restricted-children.sh failed at line %s\n" "$LINENO" >&2' ERR

# ─────────────────────────────────────────────────────────────────────────────
# Add a user as EDITOR to every restricted page in a Confluence *Cloud* page
# subtree, mirroring what the UI's "Can edit" grant does.
#
# For each descendant of the given page that carries a view/edit restriction it
#   • always adds the user to the EDIT (update) restriction, and
#   • also adds them to the VIEW (read) restriction *only when one already
#     exists*, so a view-locked page stays viewable by them.
# It never creates a read lock where none existed, and never touches
# unrestricted pages.
#
# Preview-then-confirm by default: it lists the restricted pages and the planned
# changes, then asks before writing. --apply writes without asking; --dry-run
# previews only.
#
# Usage:
#   ./add-editor-to-restricted-children.sh <page-url> <user-email> [options]
#
#   <page-url>     full Cloud page URL, e.g.
#                  https://helvetia-baloise-mig1.atlassian.net/wiki/spaces/~x/pages/6226165/MC+Setup
#   <user-email>   the user to add as editor (matched to an Atlassian accountId)
#
# Options:
#   --apply              write immediately, no confirmation prompt
#   --dry-run            preview only; never prompt, never write
#   --account-id <id>    use this Atlassian accountId directly and skip the
#                        email->accountId lookup (escape hatch)
#   -h, --help           show this help
#
# Auth (env, required):
#   ATLAS_EMAIL          your Atlassian account email
#   ATLAS_TOKEN          an Atlassian API token (id.atlassian.com -> API tokens)
# ─────────────────────────────────────────────────────────────────────────────

export LANG="${LANG:-C.UTF-8}" LC_ALL="${LC_ALL:-C.UTF-8}"

log()  { printf '\033[1;34m==>\033[0m %s\n' "$*"; }
warn() { printf '\033[1;33m[warn]\033[0m %s\n' "$*" >&2; }
die()  { printf '\033[1;31m[FAIL]\033[0m %s\n' "$*" >&2; exit 1; }
usage() { sed -n '6,36p' "$0" | sed 's/^# \{0,1\}//'; }

MODE="confirm"          # confirm | apply | dryrun
ACCOUNT_ID=""
POSITIONAL=()
while [ $# -gt 0 ]; do
  case "$1" in
    --apply)          MODE="apply";  shift ;;
    --dry-run)        MODE="dryrun"; shift ;;
    --account-id)     ACCOUNT_ID="${2:?--account-id needs a value}"; shift 2 ;;
    --account-id=*)   ACCOUNT_ID="${1#*=}"; shift ;;
    -h|--help)        usage; exit 0 ;;
    --)               shift; while [ $# -gt 0 ]; do POSITIONAL+=("$1"); shift; done ;;
    -*)               die "unknown option: $1 (see --help)" ;;
    *)                POSITIONAL+=("$1"); shift ;;
  esac
done
if [ "${#POSITIONAL[@]}" -gt 0 ]; then set -- "${POSITIONAL[@]}"; else set --; fi

PAGE_URL="${1:-}"
USER_EMAIL="${2:-}"

[ -n "$PAGE_URL" ] || { usage; die "missing <page-url>"; }
[ -n "$USER_EMAIL" ] || [ -n "$ACCOUNT_ID" ] || { usage; die "missing <user-email> (or pass --account-id)"; }

ATLAS_EMAIL="${ATLAS_EMAIL:?env var ATLAS_EMAIL is required (your Atlassian account email)}"
ATLAS_TOKEN="${ATLAS_TOKEN:?env var ATLAS_TOKEN is required (Atlassian API token)}"

PAGE_URL="$PAGE_URL" USER_EMAIL="$USER_EMAIL" ACCOUNT_ID="$ACCOUNT_ID" MODE="$MODE" \
ATLAS_EMAIL="$ATLAS_EMAIL" ATLAS_TOKEN="$ATLAS_TOKEN" \
python3 <<'PYEOF'
import json, os, re, subprocess, sys
from urllib.parse import quote, urlparse

PAGE_URL   = os.environ["PAGE_URL"]
USER_EMAIL = os.environ.get("USER_EMAIL", "").strip()
ACCOUNT_ID = os.environ.get("ACCOUNT_ID", "").strip()
MODE       = os.environ.get("MODE", "confirm")   # confirm | apply | dryrun
AUTH       = f'{os.environ["ATLAS_EMAIL"]}:{os.environ["ATLAS_TOKEN"]}'

R = "\033[1;31m"; G = "\033[1;32m"; Y = "\033[1;33m"; B = "\033[1;34m"; DIM = "\033[2m"; X = "\033[0m"
def die(msg):  print(f"{R}[FAIL]{X} {msg}", file=sys.stderr); sys.exit(1)
def warn(msg): print(f"{Y}[warn]{X} {msg}", file=sys.stderr)
def step(msg): print(f"{B}==>{X} {msg}")
def info(msg): print(f"  {msg}")

# ── HTTP via curl (proven TLS path on this host; matches the repo's other scripts) ──
def curl(method, url, *, headers=None):
    cmd = ["curl", "-s", "-w", "\n%{http_code}", "-X", method, "--user", AUTH,
           "-H", "Accept: application/json"]
    for h in (headers or []):
        cmd += ["-H", h]
    cmd.append(url)
    out = subprocess.run(cmd, capture_output=True, text=True)
    if out.returncode != 0:
        die(f"curl failed ({out.returncode}) for {method} {url}: {out.stderr.strip()}")
    body, _, code = out.stdout.rpartition("\n")
    return int(code or 0), body

def try_get_json(url):
    """GET returning (code, parsed-or-None); never raises on HTTP/parse errors."""
    code, body = curl("GET", url)
    try:
        return code, json.loads(body)
    except Exception:
        return code, None

def get_json(url):
    """GET that dies on any non-2xx (except 404 -> (404, None))."""
    code, data = try_get_json(url)
    if code == 404:
        return code, None
    if code // 100 != 2:
        die(f"HTTP {code} for GET {url}: {str(data)[:300]}")
    if data is None:
        die(f"non-JSON response for GET {url}")
    return code, data

# ── parse the page URL -> site base (…/wiki) + numeric page id ──────────────────
u = urlparse(PAGE_URL)
if not u.scheme or not u.netloc:
    die(f"not a valid URL: {PAGE_URL}")
m = re.search(r"/pages/(\d+)", u.path)
if not m:
    die(f"could not find a numeric page id (/pages/<id>) in URL: {PAGE_URL}")
PAGE_ID = m.group(1)
BASE_SITE = f"{u.scheme}://{u.netloc}"     # site root (Jira platform APIs live here)
BASE = f"{BASE_SITE}/wiki"                 # Confluence APIs

step(f"Site   : {BASE}")
step(f"Page   : {PAGE_ID}")

# validate the page (and creds) up front, and grab its title for the banner
code, page = get_json(f"{BASE}/rest/api/content/{PAGE_ID}")
if page is None:
    die(f"page {PAGE_ID} not found (or not visible to {os.environ['ATLAS_EMAIL']})")
info(f"parent: {page.get('title','?')!r}  (excluded — only its descendants are processed)")

# ── resolve email -> accountId (unless an explicit accountId was given) ─────────
_email_cache = {}
def email_of(account_id):
    if account_id not in _email_cache:
        _, d = get_json(f"{BASE}/rest/api/user/email?accountId={quote(account_id)}")
        _email_cache[account_id] = (d or {}).get("email", "") if d else ""
    return _email_cache[account_id]

def resolve_email(email):
    """Return (accountId, email) for the exact email match, or (None, 0-based-candidate-count)."""
    target = email.lower()
    tried = 0
    # 1) Jira platform user search — a *direct* email match (works when the site has Jira).
    code, d = try_get_json(f"{BASE_SITE}/rest/api/3/user/search?query={quote(email)}&maxResults=50")
    if code == 200 and isinstance(d, list):
        for usr in d:
            tried += 1
            if (usr.get("emailAddress") or "").lower() == target and usr.get("accountId"):
                return usr["accountId"], usr.get("emailAddress")
    # 2) Confluence fullname search (fuzzy on display name), then verify by email.
    #    The '~' operator needs *name* text — a full email matches nothing — so search the
    #    local part with separators turned into spaces (matthias.cullmann -> "matthias cullmann").
    local = email.split("@")[0]
    spaced = re.sub(r"[._\-+0-9]+", " ", local).strip()
    seen = set()
    for q in [x for x in (spaced, local) if x]:
        code, d = try_get_json(f"{BASE}/rest/api/search/user?cql={quote(f'user.fullname~\"{q}\"')}&limit=50")
        if code != 200 or not isinstance(d, dict):
            continue
        for res in d.get("results", []):
            usr = res.get("user") or {}
            acc = usr.get("accountId")
            if not acc or acc in seen:
                continue
            seen.add(acc); tried += 1
            em = (usr.get("email") or "").lower() or email_of(acc).lower()
            if em == target:
                return acc, (usr.get("email") or email_of(acc))
    return None, tried

confirmed_email = ""
if ACCOUNT_ID:
    resolved = ACCOUNT_ID
    step(f"User   : accountId {resolved} (from --account-id)")
    confirmed_email = email_of(resolved)
    if confirmed_email:
        info(f"email     = {confirmed_email}")
else:
    step(f"User   : resolving {USER_EMAIL} …")
    resolved, confirmed_email = resolve_email(USER_EMAIL)
    if not resolved:
        die(f"could not resolve {USER_EMAIL} to an accountId "
            f"(checked {confirmed_email} candidate(s), none with a matching email).\n"
            f"       The tenant may hide emails; re-run with --account-id <id>.")
    info(f"accountId = {resolved}")
    if confirmed_email:
        info(f"email     = {confirmed_email}")

# ── enumerate the whole subtree (CQL ancestor) ─────────────────────────────────
step("Scanning descendant pages …")
pages, start, limit = [], 0, 100
while True:
    cql = f"ancestor={PAGE_ID}"
    code, d = get_json(f"{BASE}/rest/api/content/search?cql={quote(cql)}&limit={limit}&start={start}")
    results = (d or {}).get("results", [])
    pages.extend((p["id"], p.get("title", "?")) for p in results)
    if len(results) < limit:
        break
    start += limit
info(f"{len(pages)} descendant page(s) found")

# ── inspect restrictions, build the action plan ────────────────────────────────
def op_block(restrictions, op):
    """Return (has_restriction, account_already_listed) for one operation."""
    block = (restrictions.get(op) or {}).get("restrictions") or {}
    users = (block.get("user") or {})
    groups = (block.get("group") or {})
    u_results = users.get("results", []) or []
    g_results = groups.get("results", []) or []
    u_size = users.get("size", len(u_results))
    g_size = groups.get("size", len(g_results))
    has = (u_size + g_size) > 0
    already = any(r.get("accountId") == resolved for r in u_results)  # first page only (best-effort)
    return has, already

plan = []   # {id, title, ops:[…], read_summary, update_summary, add:[…], already:bool}
for pid, title in pages:
    _, restr = get_json(f"{BASE}/rest/api/content/{pid}/restriction/byOperation")
    restr = restr or {}
    read_has, read_already   = op_block(restr, "read")
    upd_has,  upd_already    = op_block(restr, "update")
    if not (read_has or upd_has):
        continue                      # unrestricted -> skip
    add = []
    if not upd_already:
        add.append("update")
    if read_has and not read_already:
        add.append("read")
    plan.append({
        "id": pid, "title": title,
        "read": read_has, "update": upd_has,
        "add": add,          # ops to add the user to; empty => already an editor
    })

restricted = plan
to_change  = [p for p in plan if p["add"]]

# ── preview ─────────────────────────────────────────────────────────────────
def restr_label(p):
    bits = []
    if p["read"]:   bits.append("view")
    if p["update"]: bits.append("edit")
    return "+".join(bits) + "-restricted"

step(f"Restricted descendant pages: {len(restricted)}  "
     f"(of {len(pages)} scanned; {len(pages)-len(restricted)} unrestricted)")
for p in restricted:
    if p["add"]:
        action = f"{G}add {', '.join(p['add'])}{X}"
    else:
        action = f"{DIM}already editor — no change{X}"
    print(f"  • {p['id']:>12}  {p['title'][:60]:<60}  [{restr_label(p)}]  {action}")

if not to_change:
    print(f"\n{G}==>{X} Nothing to do — user is already an editor on every restricted page.")
    sys.exit(0)

# ── confirm / apply ────────────────────────────────────────────────────────────
who = confirmed_email or USER_EMAIL or resolved
print(f"\n{B}==>{X} {len(to_change)} page(s) will be modified to add "
      f"{who} ({resolved}) as editor.")

if MODE == "dryrun":
    print(f"{Y}[dry-run]{X} no changes written.")
    sys.exit(0)

if MODE == "confirm":
    try:
        with open("/dev/tty", "r") as tin, open("/dev/tty", "w") as tout:
            tout.write("Apply these changes? [y/N] "); tout.flush()
            ans = tin.readline().strip().lower()
    except OSError:
        die("no TTY available for confirmation — re-run with --apply or --dry-run")
    if ans not in ("y", "yes"):
        print("Aborted — no changes written.")
        sys.exit(0)

step("Applying …")
modified = failed = 0
for p in to_change:
    ok = True
    for op in p["add"]:
        code, body = curl("PUT",
            f"{BASE}/rest/api/content/{p['id']}/restriction/byOperation/{op}/user?accountId={quote(resolved)}",
            headers=["Content-Type: application/json"])
        if code // 100 != 2:
            ok = False
            warn(f"{p['id']} ({p['title'][:40]}): add {op} -> HTTP {code}: {body[:200]}")
    if ok:
        modified += 1
        info(f"{G}✓{X} {p['id']}  {p['title'][:60]}  (+{', '.join(p['add'])})")
    else:
        failed += 1

# ── summary ─────────────────────────────────────────────────────────────────
already_cnt = sum(1 for p in restricted if not p["add"])
print()
step("Summary")
info(f"scanned          : {len(pages)}")
info(f"restricted       : {len(restricted)}")
info(f"modified         : {modified}")
info(f"already editor   : {already_cnt}")
info(f"failed           : {failed}")
if failed:
    die(f"{failed} page(s) failed — see [warn] lines above")
print(f"{G}==>{X} Done.")
PYEOF
