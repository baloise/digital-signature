# CMA App Migration — Troubleshooting Log

This document records the steps taken to get end-to-end CMA (Cloud Migration Assistant) app migration working between the Confluence Server/DC plugin and the Forge cloud app.

## Goal

Migrate signature data from Confluence Server (Bandana storage) to Confluence Cloud (Forge SQL) via CMA, and rewrite macro keys so migrated pages render correctly.

## Environment

| Component | Value |
|-----------|-------|
| Server plugin key | `com.baloise.confluence.digital-signature` |
| Forge app ID | `bab5617e-dc42-4ca8-ad38-947c826fe58c` |
| Forge macro key | `digital-signature` |
| Server macro key (name) | `signature` / key `digital-signature` |
| CMA listener lib | `atlassian-app-cloud-migration-listener:1.8.7` |
| Spring Scanner annotation | `5.1.0` (provided) |
| Spring Scanner Maven plugin | `2.0.1` (build-time) |
| Test Confluence | 9.5.4 (Docker) |
| Target Cloud site | cul.atlassian.net (production) |

## Issue 1: OSGi service registration — `DiscoverableForgeListener` not found by CMA

### Problem
The plugin uses a hand-written `plugin-context.xml` for Spring bean wiring. Spring Scanner does not process `@ExportAsService` when `plugin-context.xml` exists, so the migration listener is never registered as an OSGi service.

### Attempts

| # | Approach | Result |
|---|----------|--------|
| 1 | `@ExportAsService` + `@ConfluenceComponent` | Annotations ignored — `plugin-context.xml` disables Spring Scanner |
| 2 | Delete `plugin-context.xml`, use annotations everywhere | HK2 conflict — macro/REST beans break |
| 3 | `<osgi:service>` in `plugin-context.xml` | Plugin won't start — CCMA doesn't export `.listener` / `.confluence` sub-packages |
| 4 | `<component>` in `atlassian-plugin.xml` | Build fails — forbidden when `Atlassian-Plugin-Key` is set with Spring Scanner |
| 5 | `<scanner:scan-packages>` in a second Spring XML | Plugin won't start — duplicate bean conflicts |
| 6 | Programmatic `BundleContext.registerService()` | ClassLoader mismatch — bundled interfaces ≠ CCMA's interfaces |
| 7 | **Build-time Spring Scanner** (`atlassian-spring-scanner-maven-plugin` v2.0.1) + `<atlassian-scanner:scan-indexes/>` in `plugin-context.xml` | **Works** — plugin starts, `@ExportAsService` processed via pre-built index |

### Resolution
Use `atlassian-spring-scanner-maven-plugin` v2.0.1 to generate `META-INF/plugin-components/exports` at build time. Replace manual beans in `plugin-context.xml` with `<atlassian-scanner:scan-indexes/>`. Add `@ComponentImport` to all constructor-injected dependencies.

### Key files
- `META-INF/plugin-components/exports` — declares `DigitalSignatureMigrationListener#DiscoverableForgeListener,ConfluenceAppCloudMigrationListenerV1`
- `META-INF/plugin-components/imports` — lists all `@ComponentImport` services
- `META-INF/spring/plugin-context.xml` — `<atlassian-scanner:scan-indexes/>`

## Issue 2: CMA dependency scope — `provided` vs `compile`

### Problem
CCMA does **not** export the sub-packages `com.atlassian.migration.app.listener`, `.confluence`, or `.gateway`. Only the flat `com.atlassian.migration.app` package is exported.

### Attempts

| # | Approach | Result |
|---|----------|--------|
| 1 | `provided` scope + `resolution:="optional"` import | `FileNotFoundException: DiscoverableForgeListener.class` — class not in JAR and not importable from CCMA |
| 2 | `compile` scope (bundled) + `!com.atlassian.migration.app.*` import exclusion | **Works** — plugin starts, CMA creates app container |

### Resolution
Keep CMA dependency at `compile` scope (bundled in JAR). Exclude from Import-Package with `!com.atlassian.migration.app.*`.

**Note:** This creates a theoretical classloader mismatch (bundled classes ≠ CCMA's classes), but CMA still discovers the listener and creates app containers. The discovery mechanism may use class-name-based lookup rather than class-identity-based lookup.

## Issue 3: `scanner-runtime` embedding error (March 31)

### Problem
`Incorrect use of atlassian-spring-scanner-runtime: atlassian-spring-scanner-runtime classes are embedded inside the target plugin`

### Resolution
Ensure `atlassian-spring-scanner-annotation` has `<scope>provided</scope>` and there is NO dependency on `atlassian-spring-scanner-runtime`. The build-time Maven plugin (v2.0.1) generates indexes; the runtime scanner is provided by the Confluence host.

## Issue 4: Gson `ClassNotFoundException` on Confluence 9.5.4

### Problem
Confluence 9.5.4 does not export Gson. The plugin fails with `ClassNotFoundException: com.google.gson.GsonBuilder`.

### Resolution
Remove `<scope>provided</scope>` from the Gson dependency (bundle it). Add `!com.google.gson.*` to `Import-Package`. Requires `-Denforcer.skip=true` because Gson is on Atlassian's banned dependency list.

## Issue 5: `getForgeEnvironmentName()` casing

### Problem
The method returned `"PRODUCTION"` (uppercase string literal). The actual constant `ForgeEnvironmentName.PRODUCTION` equals `"production"` (lowercase). The migration orchestrator routes events based on this value — wrong casing means events go to a non-existent environment.

### Discovery
Decompiled `ForgeEnvironmentName.class` from `atlassian-app-cloud-migration-listener-1.8.7.jar`:
```
ConstantValue: String production   (not PRODUCTION)
ConstantValue: String development  (not DEVELOPMENT)
ConstantValue: String staging      (not STAGING)
```

### Resolution
Changed `return "PRODUCTION"` to `return ForgeEnvironmentName.PRODUCTION`.

## Issue 6: Forge manifest — wrong migration module schema

### Problem
The `manifest.yml` declared the migration handler with `function` + `events` fields (trigger-style):
```yaml
migration:
  - key: app-data-migration-listener
    function: migrationHandler
    events:
      - avi:app-data-uploaded
```

The Forge manifest schema for `migration` modules expects a different structure with `appDataUploaded.function`. The old format was silently ignored — the handler was never registered with the platform.

### Discovery
Extracted the manifest JSON schema from `node_modules/@forge/manifest/out/schema/manifest-schema.json`. The `migration` module accepts two handler types:
- `listenerTriggered.function` — when the migration listener is triggered
- `appDataUploaded.function` — when app data is uploaded

### Resolution
```yaml
migration:
  - key: app-data-migration-listener
    appDataUploaded:
      function: migrationHandler
```

## Issue 7: CMA assessment — `hasCloudVersion: false`

### Problem
CMA REST API (`/rest/migration/latest/app`) persistently returns `hasCloudVersion: false` for our app. The assessment UI shows "Cloud availability: No" and "Migration path: Contact vendor". This prevents the app from being selected in the migration wizard (without dev mode).

The Marketplace clearly has 6 cloud versions (latest 2.7.0, Forge) under the same app key. Both `migrationPath: AUTOMATED` and `cloudMigrationAssistantCompatibilityRanges` are declared.

### Root cause
The **installed** Server plugin version (9.1.0) is not published on the Marketplace. The latest published server version is 7.0.7. CMA cannot match the installed version to any known Marketplace version, so the lookup fails.

### Attempted fix
Uploaded 9.1.0 to the Marketplace — **rejected by Atlassian with reason "Server EOL"** (Confluence Server end-of-life Feb 2024). New Server-only versions are no longer accepted.

### Current approach
Added Data Center compatibility params to `atlassian-plugin.xml`:
```xml
<param name="atlassian-data-center-status">compatible</param>
<param name="atlassian-data-center-compatible">true</param>
```
Rebuilt as v9.2.0 for upload as a Data Center version (DC is still supported on Marketplace).

### Workaround
CMA dev mode (`migration-assistant.app-migration.dev-mode` dark feature) bypasses the assessment and auto-includes any app with a `DiscoverableForgeListener`. With dev mode, app containers are created and server-side export works (reaches 80%).

## Issue 8: `avi:app-data-uploaded` event never delivered to Forge app

### Problem
Every migration reaches 80% ("Events are being processed") then times out after 15 minutes. The Forge app receives zero events in any environment (production or development). No logs appear.

### What works
- Server-side: `onStartAppMigration()` runs, data exported as JSONL.gz via `gateway.createAppData()`
- CMA orchestrator: app container created, transfer reaches 80%
- Forge app: deployed to production, installed on cul.atlassian.net, migration module declared

### Fixes applied (not yet tested together after all fixes)
1. `getForgeEnvironmentName()` → `"production"` (lowercase) ✅
2. Manifest schema → `appDataUploaded.function` ✅
3. DC compatibility params for Marketplace publishing ✅ (pending upload)

### Status
**Pending** — need to publish v9.2.0 to Marketplace as DC, then re-test. The combination of the manifest fix (Issue 6) and the casing fix (Issue 5) has not yet been tested with a proper `hasCloudVersion: true` assessment.

## Marketplace Migration API Declaration

```bash
curl -X PUT \
  'https://marketplace.atlassian.com/rest/2/addons/com.baloise.confluence.digital-signature/migration' \
  -H 'Content-Type: application/json' \
  -u '<vendor-email>:<api-token>' \
  -d '{
    "migrationPath": "AUTOMATED",
    "cloudMigrationAssistantCompatibility": "7.0.3",
    "cloudMigrationAssistantCompatibilityRanges": [
      {"start": "7.0.3", "end": null}
    ],
    "migrationDocumentation": "https://github.com/baloise/digital-signature/blob/main/docs/cloud-migration-asistant.md",
    "featureDifferenceDocumentation": "https://github.com/baloise/digital-signature/blob/main/docs/what_has_changed.md"
  }'
```

## Related Community Posts

- [How to register DiscoverableForgeListener for CMA app migration in a plugin with plugin-context.xml?](https://community.developer.atlassian.com/t/how-to-register-discoverableforgelistener-for-cma-app-migration-in-a-plugin-with-plugin-context-xml/99936) — our post, answered with build-time scanner approach
- [Forge remote endpoints not called in migration process](https://community.developer.atlassian.com/t/forge-remote-endpoints-not-called-in-migration-process/99951) — another developer with the identical symptom (March 2026, unresolved)

## Timeline

| Date | Action |
|------|--------|
| 2026-03-29 | First CMA migration attempt to devds.atlassian.net — app container created, timed out at 80% |
| 2026-03-31 | Spring Scanner runtime embedding error, multiple plugin upload attempts |
| 2026-04-04 | Build-time scanner fix, casing fix, manifest fix, DC compatibility params |
| 2026-04-04 | Marketplace rejects v9.1.0 ("Server EOL") |
| 2026-04-04 | Rebuilt as v9.2.0 with DC params for re-submission |
