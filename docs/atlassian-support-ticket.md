# Atlassian Support Ticket — CMA Event Delivery + DC Publishing

**Reference:** ECOHELP-119424
**Date:** 2026-04-05

---

**Subject:** App migration events (avi:app-data-uploaded) never delivered to Forge app — related to ECOHELP-119424

**Product:** Confluence Cloud Migration Assistant / App Migration Platform

---

Hi,

I am the vendor of **Digital Signature for Confluence** (app key: `com.baloise.confluence.digital-signature`, [Marketplace listing](https://marketplace.atlassian.com/apps/1217404/digital-signature-for-confluence)). I have implemented CMA app migration from my Server/DC plugin to my Forge cloud app and am stuck on event delivery. This is related to my existing ticket **ECOHELP-119424** regarding publishing a new Server/DC version with CMA support.

## What works

- My Server plugin (v9.2.0) implements `DiscoverableForgeListener` and `ConfluenceAppCloudMigrationListenerV1`
- CMA discovers the listener, creates an app container, and calls `onStartAppMigration()` successfully
- Signature data is exported as JSONL.gz via `gateway.createAppData("signatures")` and `gateway.completeExport()`
- The transfer reaches **80%** with status "Events are being processed"
- My Forge app (v2.7.0) is deployed to production and installed on the target Cloud site (cul.atlassian.net)
- The Forge manifest declares a `migration` module with `appDataUploaded.function`
- I also added a `webtrigger` and `connect-confluence:cloudAppMigration` module as alternative delivery paths
- `getForgeEnvironmentName()` returns `"production"` (lowercase, matching `ForgeEnvironmentName.PRODUCTION`)
- `getForgeAppId()` returns `bab5617e-dc42-4ca8-ad38-947c826fe58c` (matches the Forge app ID in manifest.yml)
- Marketplace migration path is declared as `AUTOMATED` with `cloudMigrationAssistantCompatibilityRanges: [{start: "7.0.3", end: null}]`

## What fails

The Forge app **never receives the `avi:app-data-uploaded` event**. After 15 minutes the transfer times out. I see zero invocations in `forge logs` across all environments (production and development). I have tested this across 6+ migration runs over several days, with multiple fixes applied between attempts.

## What I suspect

CMA assessment shows `hasCloudVersion: false` and "Contact vendor" for our app, despite the Marketplace clearly having 6 public cloud versions (latest 2.7.0). I believe this is because my **installed Server plugin version (9.2.0) is not published on the Marketplace** — the latest published server version is 7.0.7.

I attempted to publish v9.1.0 but it was **rejected with reason "Server EOL"**. I then added Data Center compatibility params (`atlassian-data-center-status: compatible`, `atlassian-data-center-compatible: true`) and rebuilt as v9.2.0, but the Marketplace upload form still only offers "Confluence Server" as a compatible product, not Data Center.

I believe the orchestrator refuses to route `avi:app-data-uploaded` events to my Forge app because it cannot verify the installed server version against any published Marketplace version.

## My request

1. **Can you help us publish v9.2.0 as a Data Center version?** The JAR includes CMA migration support (`DiscoverableForgeListener`) and DC compatibility params. I understand DC submissions require a technical review — I am happy to go through that process. Alternatively, can the existing v7.0.7 listing be updated to include DC compatibility so we can publish v9.2.0 as a DC update?

2. **Is the `hasCloudVersion: false` / unpublished server version the reason events are not delivered?** If not, what else could prevent the orchestrator from delivering `avi:app-data-uploaded` to our Forge app?

3. **Is the native Forge `migration` module actually supported for receiving CMA events?** All resolved community cases I found involve Connect apps with webhook endpoints registered via the Notification API — none involve native Forge apps. The Notification API (`PUT /rest/atlassian-connect/1/migration/webhook`) returns 403 for Forge apps ("This API is only available to Atlassian Connect apps"). Relevant community threads:
   - [Migration event app-data-uploaded not received](https://community.developer.atlassian.com/t/migration-event-app-data-uploaded-not-received/58760) — resolved by switching from descriptor to Notification API registration
   - [Forge remote endpoints not called in migration process](https://community.developer.atlassian.com/t/forge-remote-endpoints-not-called-in-migration-process/99951) — identical symptom to mine, unresolved (March 2026)
   - [DC-to-cloud migration: apps not registered to receive notifications](https://community.developer.atlassian.com/t/dc-to-cloud-migration-some-apps-are-not-registered-to-receive-migration-notifications/92299) — resolved by adding `cloudAppMigration` to Connect descriptor

   If native Forge migration modules are not yet supported, what is the recommended path for a Forge app to receive `app-data-uploaded` events?

## Environment details

| Component | Value |
|-----------|-------|
| Server plugin key | `com.baloise.confluence.digital-signature` |
| Forge app ID | `ari:cloud:ecosystem::app/bab5617e-dc42-4ca8-ad38-947c826fe58c` |
| Server plugin version (installed) | 9.2.0 (not on Marketplace) |
| Latest published server version | 7.0.7 |
| Forge cloud version (deployed) | 2.7.0 |
| Test Confluence Server | 9.5.4 |
| CMA version | 3.13.8 |
| Target Cloud site | cul.atlassian.net |
| Dev mode | enabled (`migration-assistant.app-migration.dev-mode`) |
| Existing ticket | ECOHELP-119424 |

Thank you for your help. The app is open source: https://github.com/baloise/digital-signature
