---
name: scriptrunner-exec
description: Run Groovy remotely on a Confluence Data Center instance (baloisenet) via the ScriptRunner script-console exec endpoint, headlessly with admin basic auth. Use to read/write Bandana, inspect pages/users, or do anything the in-product Script Console does — e.g. injecting digital-signature contracts directly into Bandana for CMA migration fixtures.
---

# ScriptRunner remote Groovy execution

confluence.baloisenet.com and int-confluence.baloisenet.com (Confluence DC 9.x) have
ScriptRunner. Arbitrary Groovy runs **headlessly with admin basic auth** — no browser
session, no `forge tunnel`, no Zscaler issues.

## Use the helper

```bash
scripts/sr-exec.sh path/to/script.groovy            # from a file
scripts/sr-exec.sh -e 'return "hello"'              # inline
echo 'return ...' | scripts/sr-exec.sh -            # from stdin
scripts/sr-exec.sh --host https://int-confluence.baloisenet.com -e 'return 1+1'
```

Defaults: host `$CONFLUENCE_URL` or `https://confluence.baloisenet.com`; creds
`$ATLAS_BALOISE_NET_COM_ADMIN_USR` / `_PWD` (admin_b028178 — same on int and prod).
Prints the Groovy `return` value (the `output`); exits non-zero and prints the
exception/HTTP error on failure.

## The raw endpoint (if you can't use the helper)

```
POST <host>/rest/scriptrunner/latest/user/exec/
  --user "admin_b028178:$PWD"
  -H 'Content-Type: application/json'  -H 'X-Atlassian-token: no-check'
  --data-raw '{"script":"return \"hi\"","scriptPath":null,"parameters":{}}'
# -> {"output":"hi","snapshot":{...,"exception":null}}
```
A non-null `snapshot.exception` means the Groovy threw — always check it.

## Getting components in Groovy

```groovy
import com.atlassian.sal.api.component.ComponentLocator
def bm = ComponentLocator.getComponent(com.atlassian.bandana.BandanaManager)
def pm = ComponentLocator.getComponent(com.atlassian.confluence.pages.PageManager)
```

## Digital-signature Bandana (the common task here)

Contracts live in **global** Bandana (`ConfluenceBandanaContext.GLOBAL_CONTEXT`), key
`signature.<hash>`, value a **GSON JSON String**:
```
{"key","hash","pageId","title","body","maxSignatures":-1,"visibilityLimit":-1,
 "signatures":{"<username>":"yyyy-MM-dd'T'HH:mm:ssz"},"missingSignatures":["*"],"notify":[]}
```
- `hash = sha256Hex(latestVersionId + ":" + title + ":" + body)` (== page id for a fresh
  v1 page). Always compute from `pageManager.getPage(id).getLatestVersionId()`.
- `signatures` is keyed by **username** (not userKey); dates use `SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssz")`.
- Read: `bm.getValue(ctx,key)` · write: `bm.setValue(ctx,key,json)` · delete: `bm.removeValue(ctx,key)`.

This is how `scripts/create-baloisenet-fixtures.sh` injects CMA test signatures directly
(no REST `sign`, so any existing username can be a signer; CMA later maps username→userKey→
Cloud account by email). The global store can hold thousands of `signature.*` keys, so scope
any bulk scan/filter by pageId/space.

## Pitfalls

- **Embedding data in the Groovy string:** don't paste raw JSON/Unicode/newlines into the
  script literal (Groovy escape processing breaks it). Base64-encode the payload and
  `new String(Base64.decoder.decode('…'), "UTF-8")` inside Groovy — the helper accepts a
  full script so build it with `python3 json.dumps`/`base64` as the fixture script does.
- **Always verify writes** with a read-back; the endpoint returns 200 even when your Groovy
  logged a logical error.
- This endpoint runs as a Confluence **system administrator** — treat it as such.
