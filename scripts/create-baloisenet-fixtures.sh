#!/usr/bin/env bash
set -euo pipefail
trap 'printf "\033[1;31m[FAIL]\033[0m create-baloisenet-fixtures.sh failed at line %s\n" "$LINENO" >&2' ERR

# ─────────────────────────────────────────────────────────────────────────────
# Creates CMA migration test fixtures on https://confluence.baloisenet.com and
# populates their signatures by writing the `signature.*` Bandana entries
# DIRECTLY via the ScriptRunner script-exec endpoint (admin creds).
#
# Why direct injection instead of the REST sign endpoint?
#   The /rest/signature/1.0/sign endpoint signs as the *authenticated* user, so
#   it requires each signer's password. Our signers are SSO/managed accounts
#   (no password) and creating local users with their emails collides with the
#   existing LDAP users (CMA then maps ambiguously). Writing Bandana directly as
#   admin lets us reference ANY existing username; the migration export re-keys
#   username -> userKey and CMA maps userKey -> Cloud accountId by email. No
#   passwords, no user creation, no email collision, and fully deterministic.
#
# Repeatability: a FRESH space per run by default (CMA migrates a space once;
# fresh pages => fresh content ids => fresh signature hashes => clean state).
# Pass an explicit key (e.g. CMAMIG) to reuse a space.
#
# Usage:   ./create-baloisenet-fixtures.sh [SPACE_KEY]
# Requires: ATLAS_BALOISE_NET_COM_ADMIN_USR and ATLAS_BALOISE_NET_COM_ADMIN_PWD
# ─────────────────────────────────────────────────────────────────────────────

export LANG="${LANG:-C.UTF-8}" LC_ALL="${LC_ALL:-C.UTF-8}"

BASE_URL="https://confluence.baloisenet.com"
ADMIN_USER="${ATLAS_BALOISE_NET_COM_ADMIN_USR:?env var ATLAS_BALOISE_NET_COM_ADMIN_USR is required}"
ADMIN_PASS="${ATLAS_BALOISE_NET_COM_ADMIN_PWD:?env var ATLAS_BALOISE_NET_COM_ADMIN_PWD is required}"
SPACE_KEY="${1:-CMAMIG$(date +%y%m%d%H%M)}"

log() { printf '\033[1;34m==>\033[0m %s\n' "$*"; }

log "Target : $BASE_URL"
log "Admin  : $ADMIN_USER"
log "Space  : $SPACE_KEY  (pass an explicit key as \$1 to reuse one)"

BASE_URL="$BASE_URL" ADMIN_USER="$ADMIN_USER" ADMIN_PASS="$ADMIN_PASS" SPACE_KEY="$SPACE_KEY" \
python3 <<'PYEOF'
import base64, json, os, subprocess, sys, time

BASE   = os.environ["BASE_URL"].rstrip("/")
USER   = os.environ["ADMIN_USER"]
PASS   = os.environ["ADMIN_PASS"]
SPACE  = os.environ["SPACE_KEY"]
AUTH   = f"{USER}:{PASS}"
EXEC   = f"{BASE}/rest/scriptrunner/latest/user/exec/"

def die(msg):
    print(f"\033[1;31m[FAIL]\033[0m {msg}", file=sys.stderr); sys.exit(1)
def info(msg):  print(f"  {msg}")
def step(msg):  print(f"\033[1;34m==>\033[0m {msg}")

# ── HTTP via curl (proven TLS path on this host) ────────────────────────────
def curl(method, url, *, data=None, headers=None, basic=True):
    cmd = ["curl", "-s", "-w", "\n%{http_code}", "-X", method]
    if basic:
        cmd += ["--user", AUTH]
    for h in (headers or []):
        cmd += ["-H", h]
    if data is not None:
        cmd += ["--data-raw", data]
    cmd.append(url)
    out = subprocess.run(cmd, capture_output=True, text=True)
    if out.returncode != 0:
        die(f"curl failed ({out.returncode}) for {method} {url}: {out.stderr.strip()}")
    body, _, code = out.stdout.rpartition("\n")
    return int(code or 0), body

def curl_json(method, url, payload=None, headers=None):
    h = ["Content-Type: application/json", "Accept: application/json"] + (headers or [])
    return curl(method, url, data=(json.dumps(payload) if payload is not None else None), headers=h)

# ── ScriptRunner Groovy exec ────────────────────────────────────────────────
def groovy(script):
    code, body = curl_json("POST", EXEC,
                           {"script": script, "scriptPath": None, "parameters": {}},
                           headers=["X-Atlassian-token: no-check"])
    if code != 200:
        die(f"exec endpoint HTTP {code}: {body[:500]}")
    try:
        d = json.loads(body)
    except Exception:
        die(f"exec returned non-JSON: {body[:500]}")
    exc = (d.get("snapshot") or {}).get("exception")
    if exc:
        die(f"Groovy exception: {exc}")
    return d.get("output")

# ── Fixtures (single source of truth: title/body/macro-params + signers) ─────
# Deterministic signature timestamps (epoch millis) -> repeatable runs.
SIG = {
    "B028178":        1749988800000,  # Matthias.Cullmann@baloise.ch  -> Cloud (known-good)
    "admin_b028178":  1749988860000,  # matthias.cullmann@baloise.com -> Cloud (pending invite; verify)
    "g004641":        1749988920000,  # g004641@baloise.com           -> Cloud (managed)
    "L001403":        1749988980000,  # no email                      -> unmapped (skip-path test)
}
BASELINE = ["B028178"]
MULTI    = ["B028178", "admin_b028178", "g004641", "L001403"]

LONG_BODY = "Lorem ipsum dolor sit amet, consectetur adipiscing elit. " * 40
MARKDOWN_BODY = "## Heading\n- item 1\n- item 2\n\n**bold** and *italic*\n\n`code block`"

# Each page: (page_title, [ (macro_title, body, extra_params{}, signers[]) , ... ])
# "Unsigned" intentionally omits signerGroups -> macro does NOT persist a Bandana
# entry -> no contract (matches the legacy fixture's intent), so it gets no signers.
PAGES = [
    ("Basic Signed Contract",   [("NDA",            "I agree to NDA terms",      {"signerGroups": "*"}, BASELINE)]),
    ("Multiple Signers Contract",[("Team Agreement", "We agree to the team rules",{"signerGroups": "*"}, MULTI)]),
    ("Unsigned Contract",       [("Pending Review",  "This contract is pending",  {},                    [])]),
    ("All Parameters Contract", [("Full Config",     "Complete test",
        {"signerGroups": "*", "inheritSigners": "readers and writers", "maxSignatures": "5",
         "visibilityLimit": "3", "signaturesVisible": "if signatory", "pendingVisible": "if signed",
         "panel": "true", "protectedContent": "false"}, BASELINE)]),
    ("No Title Contract",       [("",                "Anonymous contract",        {"signerGroups": "*"}, BASELINE)]),
    ("Unicode Contract",        [("Ünïcödé Tëst",
                                  "Ägréément: äöü ñ 你好",
                                  {"signerGroups": "*"}, BASELINE)]),
    ("Two Macros One Page",     [("First",  "First contract body",  {"signerGroups": "*"}, BASELINE),
                                 ("Second", "Second contract body", {"signerGroups": "*"}, BASELINE)]),
    ("Long Body Contract",      [("Long",     LONG_BODY,     {"signerGroups": "*"}, BASELINE)]),
    ("Markdown Body Contract",  [("Markdown", MARKDOWN_BODY, {"signerGroups": "*"}, BASELINE)]),
]

def macro_xhtml(title, body, params):
    xml = '<ac:structured-macro ac:name="signature" ac:schema-version="1">'
    if title:
        xml += f'<ac:parameter ac:name="title">{title}</ac:parameter>'
    for k, v in params.items():
        xml += f'<ac:parameter ac:name="{k}">{v}</ac:parameter>'
    if "]]>" in body:
        die(f"body for '{title}' contains ]]> which breaks CDATA")
    xml += f'<ac:plain-text-body><![CDATA[{body}]]></ac:plain-text-body>'
    xml += '</ac:structured-macro>'
    return xml

# ── 0. preflight: confirm exec works AND Bandana is writable (round-trip) ────
step("Preflight: ScriptRunner exec + Bandana write round-trip")
probe = groovy(r'''
import com.atlassian.sal.api.component.ComponentLocator
import com.atlassian.confluence.setup.bandana.ConfluenceBandanaContext
def bm = ComponentLocator.getComponent(com.atlassian.bandana.BandanaManager)
def ctx = ConfluenceBandanaContext.GLOBAL_CONTEXT
def k = "signature.ZZPROBE_FIXTURE_SETUP"
bm.setValue(ctx, k, "probe")
def rb = bm.getValue(ctx, k)
bm.removeValue(ctx, k)
return (rb == "probe" && bm.getValue(ctx, k) == null) ? "WRITABLE" : "NOT_WRITABLE"
''')
if probe != "WRITABLE":
    die(f"Bandana not writable via exec (got: {probe})")
info("exec + Bandana write confirmed")

# ── 1. space (idempotent) + home page ───────────────────────────────────────
step(f"Ensuring space '{SPACE}'")
code, _ = curl_json("POST", f"{BASE}/rest/api/space", {
    "key": SPACE, "name": "CMA Migration Fixtures",
    "description": {"plain": {"value": "Test data for CMA end-to-end testing", "representation": "plain"}},
})
if code not in (200, 201) and code != 400:   # 400 = already exists
    die(f"space create HTTP {code}")
code, body = curl("GET", f"{BASE}/rest/api/space/{SPACE}?expand=homepage")
if code != 200:
    die(f"space lookup HTTP {code}: {body[:300]}")
home_id = json.loads(body)["homepage"]["id"]
info(f"home page id = {home_id}")

# ── 2. create pages, capture ids ────────────────────────────────────────────
step("Creating fixture pages")
specs = []        # [{pageId,title,body,signers{username:millis}}] one per signed macro
pages_made = []
for page_title, macros in PAGES:
    storage = "".join(macro_xhtml(t, b, p) for (t, b, p, _s) in macros)
    payload = {"type": "page", "title": f"{page_title}", "ancestors": [{"id": home_id}],
               "space": {"key": SPACE}, "body": {"storage": {"value": storage, "representation": "storage"}}}
    code, body = curl_json("POST", f"{BASE}/rest/api/content", payload)
    if code not in (200, 201):
        die(f"create '{page_title}' HTTP {code}: {body[:300]}")
    pid = json.loads(body)["id"]
    pages_made.append((pid, page_title))
    info(f"{page_title}: id={pid}")
    for (mtitle, mbody, _p, signers) in macros:
        if signers:
            specs.append({"pageId": int(pid), "title": mtitle, "body": mbody,
                          "signers": {u: SIG[u] for u in signers}})

# ── 3. render pages (guarded) so the macro persists base Bandana entries ─────
step("Rendering pages (creates the petition-mode Bandana entries)")
for pid, title in pages_made:
    ok = False
    for attempt in range(3):
        code, _ = curl("GET", f"{BASE}/rest/api/content/{pid}?expand=body.view",
                       headers=["Accept: application/json"])
        if code == 200:
            ok = True; break
        time.sleep(1.5)
    if not ok:
        die(f"render failed for '{title}' (id={pid})")
info(f"rendered {len(pages_made)} pages")

# ── 4. inject signatures directly into Bandana (one exec call) ───────────────
step("Injecting signatures into Bandana via ScriptRunner")
specs_b64 = base64.b64encode(json.dumps(specs, ensure_ascii=False).encode("utf-8")).decode("ascii")
GROOVY = r'''
import com.atlassian.sal.api.component.ComponentLocator
import com.atlassian.confluence.pages.PageManager
import com.atlassian.confluence.setup.bandana.ConfluenceBandanaContext
import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import java.security.MessageDigest
import java.text.SimpleDateFormat

def bm  = ComponentLocator.getComponent(com.atlassian.bandana.BandanaManager)
def pm  = ComponentLocator.getComponent(PageManager)
def ctx = ConfluenceBandanaContext.GLOBAL_CONTEXT
def sha256 = { String s -> MessageDigest.getInstance("SHA-256").digest(s.getBytes("UTF-8")).collect{ String.format("%02x", it) }.join() }
def fmt = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssz")

def specs = new JsonSlurper().parseText(new String(Base64.decoder.decode('__SPECS_B64__'), "UTF-8"))
def results = []
specs.each { spec ->
    def pid = spec.pageId as long
    def page = pm.getPage(pid)
    if (page == null) { results << [pageId: pid, title: spec.title, status: "ERROR_PAGE_NOT_FOUND"]; return }
    def lvid  = page.getLatestVersionId()
    def title = (spec.title == null ? "" : spec.title)
    def body  = spec.body
    def key   = "signature." + sha256("" + lvid + ":" + title + ":" + body)
    def existing = bm.getValue(ctx, key)
    if (existing == null) { results << [pageId: pid, title: title, key: key, status: "ERROR_KEY_NOT_FOUND_AFTER_RENDER"]; return }
    def obj = new JsonSlurper().parseText(existing.toString())
    def sigs = new LinkedHashMap()
    spec.signers.each { u, ms -> sigs[u] = fmt.format(new Date(ms as long)) }
    obj.signatures = sigs
    if (!(obj.missingSignatures)) obj.missingSignatures = ["*"]
    bm.setValue(ctx, key, JsonOutput.toJson(obj))
    def rb = new JsonSlurper().parseText(bm.getValue(ctx, key).toString())
    results << [pageId: pid, title: title, key: key, status: "OK", signers: (rb.signatures.keySet() as List).sort()]
}
return JsonOutput.toJson(results)
'''.replace("__SPECS_B64__", specs_b64)

out = groovy(GROOVY)
results = json.loads(out)
bad = [r for r in results if r.get("status") != "OK"]
for r in results:
    info(f"{r.get('status'):>34}  {r.get('title','?')!r:30}  {sorted((r.get('signers') or []))}")
if bad:
    die(f"{len(bad)} contract(s) failed injection (see above)")

# ── 5. verify each contract via the macro's export endpoint (DC read path) ───
step("Verifying via /rest/signature/1.0/export")
problems = []
for r in results:
    code, body = curl("GET", f"{BASE}/rest/signature/1.0/export?key={r['key']}",
                      headers=["Accept: text/html"])
    if code != 200 or body.startswith("ERROR"):
        problems.append(f"{r['title']}: export HTTP {code} / {body[:60]!r}")
# Unsigned page must have NO entry (no signerGroups -> not persisted)
unsigned_pid = next(pid for pid, t in pages_made if t == "Unsigned Contract")
chk = groovy(r'''
import com.atlassian.sal.api.component.ComponentLocator
import com.atlassian.confluence.setup.bandana.ConfluenceBandanaContext
import com.atlassian.confluence.pages.PageManager
import java.security.MessageDigest
def bm = ComponentLocator.getComponent(com.atlassian.bandana.BandanaManager)
def pm = ComponentLocator.getComponent(PageManager)
def ctx = ConfluenceBandanaContext.GLOBAL_CONTEXT
def sha256 = { String s -> MessageDigest.getInstance("SHA-256").digest(s.getBytes("UTF-8")).collect{ String.format("%02x", it) }.join() }
def p = pm.getPage(__UNSIGNED_PID__L)
def key = "signature." + sha256("" + p.getLatestVersionId() + ":Pending Review:This contract is pending")
return bm.getValue(ctx, key) == null ? "NO_ENTRY_OK" : "UNEXPECTED_ENTRY"
'''.replace("__UNSIGNED_PID__", str(unsigned_pid)))
if chk != "NO_ENTRY_OK":
    problems.append(f"Unsigned page unexpectedly has a Bandana entry ({chk})")
if problems:
    die("verification failed:\n   " + "\n   ".join(problems))
info(f"{len(results)} contracts verified; Unsigned correctly has no contract")

# ── 6. summary + expected Cloud-migration counts ────────────────────────────
total_sigs = sum(len(s["signers"]) for s in specs)
step("Summary")
print(f"""  Space:            {SPACE}   ({BASE}/display/{SPACE})
  Pages created:    {len(pages_made)}
  Contracts:        {len(specs)}  (Unsigned persists none; Two-Macros = 2)
  Signatures (DC):  {total_sigs}  -> B028178 x{sum('B028178' in s['signers'] for s in specs)}, admin_b028178 x1, g004641 x1, L001403 x1

  Expected after CMA migration ([migration] Done: line), contracts always {len(specs)}:
    admin_b028178 maps  -> inserted 11, skipped 1  (L001403 has no email)
    admin_b028178 absent-> inserted 10, skipped 2  (admin + L001403)
  (B028178 .ch and g004641 always map; only the .com admin invite is uncertain.)

  Next: create a NEW CMA migration plan for space {SPACE} (whole page tree) +
        the Digital Signature app; user phase = "Select all users".
""")
print("\033[1;32m==> Done. Fixtures ready for CMA migration testing.\033[0m")
PYEOF
