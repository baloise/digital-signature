# Helvetia CMA Migration-Tool Failure — Analysis

Analysis of the *"Task timed out after 25.00 seconds"* error Sylvie (Helvetia) hit when
running the post-CMA **Migration Tools → Scan for Legacy Macros** admin screen after
CMA-migrating the `DIG` space.

## Summary

There are **two independent failures**, and Sylvie hit both:

1. **The error she saw is a scan timeout.** She left the *Space key* field **empty**, so
   the scan ran across the **whole site**. A site-wide scan does not finish inside
   Forge's hard **25-second** function limit, so it aborts with
   *"Task timed out after 25.00 seconds"*. A **space-scoped** scan (`DIG`) completes in
   about **1 second** and finds the page.
   - **Immediate workaround:** type `DIG` into the *Space key* field before clicking
     *Scan for Legacy Macros*.

2. **The real blocker is the convert step**, which fails with **`410 Gone`**: it reads
   and writes page content through a **Confluence Cloud v1 REST endpoint that Atlassian
   has now removed**. Even after a successful scoped scan, converting the found page
   fails. This needs a code fix in the Forge app (migrate to REST API **v2**).

The fixes are in the **Forge cloud app** (`/Users/matthias/git/digital-signature`), not
in this Server/DC plugin repo.

## Environment

| Component | Value |
|-----------|-------|
| Cloud site | `helvetia-baloise-mig1.atlassian.net` |
| Forge app ID | `bab5617e-dc42-4ca8-ad38-947c826fe58c` |
| Forge app version | `4.1.0` |
| Forge environment ID | `3db24628-d68b-465a-8bfd-ffb0aae164b4` |
| Reported RequestId | `260d056b-27e2-4c64-a485-07b8f5f46a15` |
| Migrated space | `DIG` |
| Log analysed | `logs/bab5617e-…-2026-06-09T13:28:45.201927998Z.log` |
| Forge code | `/Users/matthias/git/digital-signature/src/resolvers/migrationResolver.js` |

## Issue A — Scan times out when no space key is given

### Problem
The reported error *"Task timed out after 25.00 seconds"* occurs when the *Space key*
field is empty (the screenshot shows it empty).

### Evidence (from the log)
- Sylvie's RequestId `260d056b` is line 11:
  `ERROR … resolver confluence:globalSettings Function timed out. Limit of 25.00 seconds`.
- The scan immediately before it (line 12) ran CQL **with no space filter**:
  `type=page AND (macro="signature" OR macro="digital-signature")`.
- **Every** all-spaces scan in the log times out and never logs a result:
  lines 11/12, 23/24, 35–37, 63/64, 75/76, 90/91.
- **Space-scoped** scans succeed quickly:
  - line 79: `CQL: type=page AND space="DIG" AND (macro=…)` → line 78: `Found 1 pages with legacy macros`
  - line 52 → line 51: same, `Found 1 pages`.

### Root cause
`handleScan` (`migrationResolver.js:59-116`) paginates over **all** CQL results with
`expand=body.storage` inside a **single** `confluence:globalSettings` resolver
invocation. That resolver has a hard **25-second** limit. Scoped to one space the work
is tiny; site-wide it exceeds the budget and Forge kills the function.

By contrast, the **convert** step is already incremental (`CONVERT_BATCH_SIZE = 5`, the
frontend calls the resolver repeatedly with an advancing `offset`). The scan was never
given the same treatment.

### Workaround (today, no deploy)
Enter the space key (`DIG`) before scanning. This is the path that already succeeds in
the log.

### Code fix (Forge app)
Make the scan incremental like convert:
- Return after a bounded number of CQL pages (e.g. one page of `CQL_PAGE_SIZE`) along
  with the next `start` cursor, and let the frontend call repeatedly and accumulate.
- And/or drop the heavy `expand=body.storage` from the search request and fetch bodies
  only when needed.
- Optionally make the UI nudge the admin toward entering a space key.

## Issue B — Convert fails with `410 Gone` (deprecated endpoint removed)

### Problem
Even when the scoped scan finds the page, converting it fails.

### Evidence (from the log)
- Lines 49, 50, 77:
  `[migration-convert] Failed to fetch page 158728196: 410 … GoneException: This deprecated endpoint has been removed`.

### Root cause
The convert step uses the **Confluence Cloud v1 content API**, which is **removed
(`410 Gone`)** on this site. Call sites in `migrationResolver.js`:
- **GET body** — line 142: `/wiki/rest/api/content/${pageId}?expand=body.storage,version`
- **PUT update** — line 168: `/wiki/rest/api/content/${pageId}`

The scan's CQL search (line 77, `/wiki/rest/api/content/search`) still works — only the
per-page content **GET/PUT** endpoints return 410. The app uses v1 exclusively; there is
no `/wiki/api/v2` usage anywhere in `src/`.

### Code fix (Forge app) — migrate convert to REST API v2
- **GET:** `GET /wiki/api/v2/pages/{id}?body-format=storage`
  → read `body.storage.value`, `version.number`, `title`, `status`.
- **PUT:** `PUT /wiki/api/v2/pages/{id}` with a v2 payload:
  ```json
  {
    "id": "<id>",
    "status": "current",
    "title": "<title>",
    "body": { "representation": "storage", "value": "<converted>" },
    "version": { "number": <n+1>, "message": "Digital Signature macro migration" }
  }
  ```
- Keep CQL discovery on the search endpoint (it is not affected).

### Related deprecation risk (out of scope, flag only)
The v1 `/restriction/byOperation` endpoint is also used in
`signatureAuthorization.js:49` and `getPendingSignersResolver.js:76`. Restrictions v1 is
not yet removed, but it carries the same deprecation risk and should be migrated to v2 in
a follow-up.

## Where the fixes go

Both fixes are in the **Forge cloud app** repo `/Users/matthias/git/digital-signature`,
file `src/resolvers/migrationResolver.js`. They are **not** in this repo
(`digital-signature-legacy`), which is the Server/DC plugin responsible only for the
server-side CMA *export*.

## Suggested reply to Sylvie

> Hi Sylvie, thanks for the detailed report and the Forge logs — they made this easy to
> pin down.
>
> The "timed out after 25 seconds" message comes from the *Scan for Legacy Macros* step
> running across **all spaces** because the *Space key* field was left empty. On a large
> site that scan can't finish within the 25-second limit Forge gives the function. If you
> type the space key **`DIG`** into that field and scan again, it completes in about a
> second and finds your migrated page.
>
> One heads-up: there is a second issue we're already fixing on our side. The "convert"
> step currently uses an older Confluence Cloud API that Atlassian has just retired
> (it returns "410 Gone" in your logs), so the actual conversion of the page won't
> succeed yet even after a scoped scan. We're updating the app to the current API and
> will let you know when the new version is deployed so you can re-run the migration.
> Page history is preserved throughout, so nothing is at risk in the meantime.
>
> Thanks again — best, Matthias
