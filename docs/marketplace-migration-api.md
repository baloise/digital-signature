# Declare Migration Path via Marketplace Migrations REST API

## Endpoint

```
PUT https://marketplace.atlassian.com/rest/2/addons/{addonKey}/migration
```

Where `{addonKey}` = `com.baloise.confluence.digital-signature`

## Authentication

Marketplace vendor API key (Basic auth with vendor email + API token from https://marketplace.atlassian.com/manage/vendor).

## Request Body

```json
{
  "migrationPath": "AUTOMATED",
  "cloudMigrationAssistantCompatibilityRanges": [
    {
      "start": "9.1.0",
      "end": null
    }
  ],
  "migrationDocumentation": "https://github.com/baloise/digital-signature/blob/main/docs/cloud-migration-asistant.md",
  "featureDifferenceDocumentation": "https://github.com/baloise/digital-signature/blob/main/docs/what_has_changed.md"
}
```

### Fields

| Field | Value | Description |
|-------|-------|-------------|
| `migrationPath` | `AUTOMATED` | Tells CMA our app has an automated migration path |
| `cloudMigrationAssistantCompatibilityRanges` | `[{"start": "9.1.0", "end": null}]` | Server plugin versions that support CMA migration (9.1.0+) |
| `migrationDocumentation` | URL | Link to our migration guide |
| `featureDifferenceDocumentation` | URL | Link to feature comparison doc |

## curl Example

```bash
curl -X PUT \
  'https://marketplace.atlassian.com/rest/2/addons/com.baloise.confluence.digital-signature/migration' \
  -H 'Content-Type: application/json' \
  -u 'vendor-email:api-token' \
  -d '{
    "migrationPath": "AUTOMATED",
    "cloudMigrationAssistantCompatibilityRanges": [
      {"start": "9.1.0", "end": null}
    ],
    "migrationDocumentation": "https://github.com/baloise/digital-signature/blob/main/docs/cloud-migration-asistant.md",
    "featureDifferenceDocumentation": "https://github.com/baloise/digital-signature/blob/main/docs/what_has_changed.md"
  }'
```

## What This Does

After this API call:
1. CMA Assessment will show **"Automated"** in the Migration Path column (instead of "Contact vendor")
2. CMA's cloud orchestrator will create an **app container** during migration
3. The orchestrator will trigger `onStartAppMigration()` on our Server plugin's `DigitalSignatureMigrationListener`
4. The listener will export signature data as JSONL.gz
5. The Forge app's migration handler will receive and import the data

## Prerequisites

- Marketplace vendor account with API access
- The app must be listed on the Atlassian Marketplace
- For testing: this declaration affects ALL customers, not just dev mode

## Alternative: Dev Mode Testing

For dev-mode testing WITHOUT the Marketplace declaration, the Atlassian migration orchestrator still requires app containers. The "Skipping App container" log message confirms the orchestrator ignores apps without a declared migration path, even in dev mode.

This is a gap in the dev mode feature — it bypasses local CMA checks but not the cloud-side orchestrator's app container creation logic.

## References

- [Manage your app info](https://developer.atlassian.com/platform/app-migration/manage-app-info/)
- [Migration path readiness checklist](https://developer.atlassian.com/platform/app-migration/readiness-checklist/)
- [Marketplace Migrations REST API](https://developer.atlassian.com/platform/marketplace/rest/v4/api-group-migrations/)
