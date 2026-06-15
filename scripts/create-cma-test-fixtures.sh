#!/usr/bin/env bash
set -euo pipefail

# Creates comprehensive test fixtures for CMA end-to-end testing.
# Targets a local Confluence 10 Server instance with the digital-signature plugin installed.
#
# Usage: ./create-cma-test-fixtures.sh [base-url] [space-key]
#   base-url  defaults to http://localhost:10090
#   space-key defaults to CMA. Pass a fresh key per run (e.g. CMA<runid>) so the
#             repeatable e2e migration test lands each run in its own Cloud space.

BASE_URL="${1:-http://localhost:10090}"
SPACE_KEY="${2:-CMA}"
ADMIN_USER="admin"
ADMIN_PASS="admin"
AUTH="${ADMIN_USER}:${ADMIN_PASS}"

# ── Helpers ──────────────────────────────────────────────────────────────────

api() {
    local method="$1" path="$2"; shift 2
    curl -s --user "$AUTH" \
        -H 'Content-Type: application/json' \
        -H 'Accept: application/json' \
        -X "$method" \
        "$@" \
        "${BASE_URL}${path}"
}

create_user() {
    local username="$1" fullname="$2" email="$3" password="${4:-test1234}"
    echo "  Creating user: ${username} (${email})"
    # Confluence Server REST API for user creation
    api POST "/rest/api/admin/user" \
        -d "{\"userName\":\"${username}\",\"fullName\":\"${fullname}\",\"email\":\"${email}\",\"password\":\"${password}\"}" \
        2>/dev/null || echo "    (user may already exist)"
}

create_page() {
    local title="$1" body_storage="$2"
    echo "  Creating page: ${title}" >&2
    local payload
    payload=$(python3 -c "
import json, sys
print(json.dumps({
    'type': 'page',
    'title': sys.argv[1],
    'space': {'key': '${SPACE_KEY}'},
    'body': {'storage': {'value': sys.argv[2], 'representation': 'storage'}}
}))
" "$title" "$body_storage")
    local response
    response=$(api POST "/rest/api/content" -d "$payload")
    local page_id
    page_id=$(echo "$response" | python3 -c "import sys,json; d=json.load(sys.stdin); print(d.get('id', 'ERROR: ' + d.get('message','unknown')))" 2>/dev/null)
    if [[ "$page_id" == ERROR* ]]; then
        echo "    FAILED: ${page_id}" >&2
        echo ""
        return 1
    fi
    echo "    Page ID: ${page_id}" >&2
    echo "$page_id"
}

trigger_render() {
    local page_id="$1"
    # Fetching with expand=body.view triggers the macro execution,
    # which creates the Signature2 Bandana entry
    curl -sf --user "$AUTH" \
        -H 'Accept: application/json' \
        "${BASE_URL}/rest/api/content/${page_id}?expand=body.view" > /dev/null
    echo "    Triggered render for page ${page_id}"
}

sign_as() {
    local username="$1" password="$2" sig_key="$3"
    echo "    Signing ${sig_key} as ${username}"
    # The /sign endpoint signs as the authenticated user and redirects
    curl -sf --user "${username}:${password}" \
        -L -o /dev/null \
        "${BASE_URL}/rest/signature/1.0/sign?key=${sig_key}" \
        2>/dev/null || echo "      WARNING: sign failed for ${username} on ${sig_key}"
}

# Build macro XHTML. Args: title, body, [extra params as key=value pairs]
macro_xhtml() {
    local title="$1" body="$2"; shift 2
    local xml='<ac:structured-macro ac:name="signature" ac:schema-version="1">'
    if [ -n "$title" ]; then
        xml+="<ac:parameter ac:name=\"title\">${title}</ac:parameter>"
    fi
    for param in "$@"; do
        local pname="${param%%=*}" pval="${param#*=}"
        xml+="<ac:parameter ac:name=\"${pname}\">${pval}</ac:parameter>"
    done
    xml+="<ac:plain-text-body><![CDATA[${body}]]></ac:plain-text-body>"
    xml+='</ac:structured-macro>'
    echo "$xml"
}

# Compute signature key the same way Signature2.java does: sha256("pageId:title:body")
sig_key() {
    local page_id="$1" title="$2" body="$3"
    local hash
    hash=$(printf '%s' "${page_id}:${title}:${body}" | shasum -a 256 | awk '{print $1}')
    echo "signature.${hash}"
}

# ── Main ─────────────────────────────────────────────────────────────────────

echo "==> CMA Test Fixtures"
echo "    Target: ${BASE_URL}"
echo ""

# Step 1: Create test users
echo "==> Step 1: Creating test users"
create_user "esther"  "Esther Lol"    "esther.l0l@mail.ch"
create_user "thomas"  "Thomas Lol"    "thomas.l0l@mail.ch"
create_user "unmapped" "Unmapped User" "nobody@example.invalid"
echo ""

# Step 2: Create test space
echo "==> Step 2: Creating test space '${SPACE_KEY}'"
api POST "/rest/api/space" \
    -d "{\"key\":\"${SPACE_KEY}\",\"name\":\"CMA Test Fixtures\",\"description\":{\"plain\":{\"value\":\"Test data for Cloud Migration Assistant\",\"representation\":\"plain\"}}}" \
    2>/dev/null || echo "  (space may already exist)"
echo ""

# Step 3: Create pages
echo "==> Step 3: Creating test pages"

# Arrays to track page IDs and their signature keys for signing later
declare -a PAGE_IDS
declare -a PAGE_TITLES
declare -a SIG_KEYS

# ── Page 1: Basic signed ──
BODY1="I agree to NDA terms"
TITLE1="NDA"
MACRO1=$(macro_xhtml "$TITLE1" "$BODY1" "signerGroups=*")
PID1=$(create_page "Basic Signed Contract" "$MACRO1")
PAGE_IDS+=("$PID1"); PAGE_TITLES+=("Basic Signed")

# ── Page 2: Multiple signers ──
BODY2="We agree to the team rules"
TITLE2="Team Agreement"
MACRO2=$(macro_xhtml "$TITLE2" "$BODY2" "signerGroups=*")
PID2=$(create_page "Multiple Signers Contract" "$MACRO2")
PAGE_IDS+=("$PID2"); PAGE_TITLES+=("Multiple Signers")

# ── Page 3: Unsigned ──
BODY3="This contract is pending"
TITLE3="Pending Review"
MACRO3=$(macro_xhtml "$TITLE3" "$BODY3")
PID3=$(create_page "Unsigned Contract" "$MACRO3")
PAGE_IDS+=("$PID3"); PAGE_TITLES+=("Unsigned")

# ── Page 4: All parameters ──
BODY4="Complete test"
TITLE4="Full Config"
MACRO4=$(macro_xhtml "$TITLE4" "$BODY4" \
    "signerGroups=*" \
    "inheritSigners=readers and writers" \
    "maxSignatures=5" \
    "visibilityLimit=3" \
    "signaturesVisible=if signatory" \
    "pendingVisible=if signed" \
    "panel=true" \
    "protectedContent=false")
PID4=$(create_page "All Parameters Contract" "$MACRO4")
PAGE_IDS+=("$PID4"); PAGE_TITLES+=("All Parameters")

# ── Page 5: No title ──
BODY5="Anonymous contract"
TITLE5=""
MACRO5=$(macro_xhtml "" "$BODY5" "signerGroups=*")
PID5=$(create_page "No Title Contract" "$MACRO5")
PAGE_IDS+=("$PID5"); PAGE_TITLES+=("No Title")

# ── Page 6: Unicode ──
BODY6="Ägréément: äöü ñ 你好"
TITLE6="Ünïcödé Tëst"
MACRO6=$(macro_xhtml "$TITLE6" "$BODY6" "signerGroups=*")
PID6=$(create_page "Unicode Contract" "$MACRO6")
PAGE_IDS+=("$PID6"); PAGE_TITLES+=("Unicode")

# ── Page 7: Two macros on one page ──
BODY7A="First contract body"
TITLE7A="First"
BODY7B="Second contract body"
TITLE7B="Second"
MACRO7A=$(macro_xhtml "$TITLE7A" "$BODY7A" "signerGroups=*")
MACRO7B=$(macro_xhtml "$TITLE7B" "$BODY7B" "signerGroups=*")
PID7=$(create_page "Two Macros One Page" "${MACRO7A}${MACRO7B}")
PAGE_IDS+=("$PID7"); PAGE_TITLES+=("Two Macros")

# ── Page 8: Long body ──
BODY8=$(python3 -c "print('Lorem ipsum dolor sit amet, consectetur adipiscing elit. ' * 40)")
TITLE8="Long"
MACRO8=$(macro_xhtml "$TITLE8" "$BODY8" "signerGroups=*")
PID8=$(create_page "Long Body Contract" "$MACRO8")
PAGE_IDS+=("$PID8"); PAGE_TITLES+=("Long Body")

# ── Page 9: Markdown body ──
BODY9='## Heading
- item 1
- item 2

**bold** and *italic*

`code block`'
TITLE9="Markdown"
MACRO9=$(macro_xhtml "$TITLE9" "$BODY9" "signerGroups=*")
PID9=$(create_page "Markdown Body Contract" "$MACRO9")
PAGE_IDS+=("$PID9"); PAGE_TITLES+=("Markdown")

echo ""

# Step 4: Trigger renders to create Bandana entries
echo "==> Step 4: Triggering macro renders"
for pid in "${PAGE_IDS[@]}"; do
    trigger_render "$pid"
done
# Wait a moment for Bandana writes to complete
sleep 2
echo ""

# Step 5: Compute signature keys and sign contracts
echo "==> Step 5: Signing contracts"

# Extract actual page IDs (create_page outputs multiple lines, last is the ID)
# Re-extract clean page IDs
get_last_line() { echo "$1" | tail -1; }

PID1=$(get_last_line "$PID1")
PID2=$(get_last_line "$PID2")
PID3=$(get_last_line "$PID3")
PID4=$(get_last_line "$PID4")
PID5=$(get_last_line "$PID5")
PID6=$(get_last_line "$PID6")
PID7=$(get_last_line "$PID7")
PID8=$(get_last_line "$PID8")
PID9=$(get_last_line "$PID9")

KEY1=$(sig_key "$PID1" "$TITLE1" "$BODY1")
KEY2=$(sig_key "$PID2" "$TITLE2" "$BODY2")
# Page 3: unsigned — no signing
KEY4=$(sig_key "$PID4" "$TITLE4" "$BODY4")
KEY5=$(sig_key "$PID5" "$TITLE5" "$BODY5")
KEY6=$(sig_key "$PID6" "$TITLE6" "$BODY6")
KEY7A=$(sig_key "$PID7" "$TITLE7A" "$BODY7A")
KEY7B=$(sig_key "$PID7" "$TITLE7B" "$BODY7B")
KEY8=$(sig_key "$PID8" "$TITLE8" "$BODY8")
KEY9=$(sig_key "$PID9" "$TITLE9" "$BODY9")

echo "  Page 1 (Basic): ${KEY1}"
sign_as admin admin "$KEY1"

echo "  Page 2 (Multiple):"
sign_as admin   admin    "$KEY2"
sign_as esther  test1234 "$KEY2"
sign_as thomas  test1234 "$KEY2"

echo "  Page 3 (Unsigned): skipped"

echo "  Page 4 (All params): ${KEY4}"
sign_as admin admin "$KEY4"

echo "  Page 5 (No title): ${KEY5}"
sign_as admin admin "$KEY5"

echo "  Page 6 (Unicode): ${KEY6}"
sign_as admin admin "$KEY6"

echo "  Page 7 (Two macros):"
sign_as admin admin "$KEY7A"
sign_as admin admin "$KEY7B"

echo "  Page 8 (Long body): ${KEY8}"
sign_as admin admin "$KEY8"

echo "  Page 9 (Markdown): ${KEY9}"
sign_as admin admin "$KEY9"

echo ""

# Step 6: Verify
echo "==> Step 6: Verification summary"
echo ""
echo "  Pages created: 9"
echo "  Contracts expected: 10 (page 7 has 2 macros)"
echo "  Signatures expected:"
echo "    - admin: 9 signatures (all except page 3)"
echo "    - esther: 1 signature (page 2)"
echo "    - thomas: 1 signature (page 2)"
echo "    - unmapped: 0 signatures (in missing set only)"
echo ""
echo "  Space: ${BASE_URL}/display/${SPACE_KEY}"
echo ""
echo "==> Done! Test fixtures are ready for CMA migration."
