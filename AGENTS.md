# AGENTS.md — Digital Signature (Confluence Data Center plugin)

Java/Maven Atlassian plugin (`packaging: atlassian-plugin`, key
`com.baloise.confluence.digital-signature`). Signature/contract data is stored in **global
Bandana** under `signature.<hash>` keys. A companion **Forge Cloud** app lives in the sibling
repo `../digital-signature` and imports this data during a Cloud Migration Assistant (CMA)
migration.

## Build & test

```bash
mvn -B clean test                     # unit tests
mvn -B clean package -DskipTests      # build the JAR -> target/digital-signature-<version>.jar
scripts/test-plugin.sh build 9.5.4    # build + stage for Confluence 9 (10.x via the confluence10 profile)
scripts/test-plugin.sh start 9.5.4    # local dockerised Confluence (port 9090) for manual testing
```
Confluence 9 uses spring-scanner. **REST resources and components must be annotated with
`@ConfluenceComponent` and inject collaborators via `@ComponentImport`**, or they fail at
runtime (Jersey 500). See `.claude/memory`/the repo memory note.

## Scripts

| Script | Purpose |
|---|---|
| `scripts/test-plugin.sh` | Local dockerised Confluence: build / start / upload / verify / teardown. |
| `scripts/release-dc.sh` | Release to a real instance: `test` / `build` / `upload <host>` / `verify <host>` via the UPM REST API. |
| `scripts/smoke-test.sh` | Post-deploy check on the sandbox page: rendering + signing. |
| `scripts/sr-exec.sh` | Run Groovy remotely via the ScriptRunner exec endpoint (admin basic auth). |
| `scripts/create-baloisenet-fixtures.sh` | Create CMA test fixtures on baloisenet DC, injecting signatures directly into Bandana via ScriptRunner. |
| `scripts/create-cma-test-fixtures.sh` | Same fixtures for a LOCAL dockerised Confluence (used by the Cloud repo's e2e). |

## Skills (`.claude/skills/`)

- **release-dc-version** — gated release flow: test+build → upload int → smoke int → deploy
  prod → smoke prod. Invoke when asked to release/deploy a new DC version.
- **scriptrunner-exec** — run Groovy on baloisenet DC headlessly; read/write Bandana, etc.

## Environments

- INT  `https://int-confluence.baloisenet.com`
- PROD `https://confluence.baloisenet.com` (live corporate wiki — confirm before deploy)
- Sandbox page on both: `1383204089` ("Digital Signature Sandbox") — used by the smoke test.
- Admin **`admin_b028178`** on both: `$ATLAS_BALOISE_NET_COM_ADMIN_USR` / `_PWD`.

## Domain gotchas

- **Signature key:** `signature.` + `sha256Hex(latestVersionId + ":" + title + ":" + body)`
  (`Signature2.java`, `DigitalSignatureMacro.java`). `latestVersionId == content id` only for
  a fresh v1 page → **never edit a fixture page after creating it**.
- **Persistence:** the macro only writes a Bandana entry when `hasMissingSignatures()` is true
  — petition mode (`signerGroups=*`) qualifies; a macro with no signers persists nothing
  (so an "unsigned" fixture yields no contract).
- **CMA user mapping:** CMA links a Server user to an existing Cloud account **by email** during
  the "Select all users" phase; the Cloud handler resolves `userKey → accountId` via
  `getMappingById('identity:user','confluence.userkey/<userKey>')`. Sign with usernames whose
  email matches a real Cloud account.
- **CMA export scope:** `DigitalSignatureMigrationListener` scopes the export to the migrated
  spaces (via `getPaginatedContainers`), falling back to "export all" for a full-site migration
  or if scope can't be resolved. The global Bandana store can hold thousands of `signature.*`
  entries — don't assume a small export.
