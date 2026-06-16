---
name: release-dc-version
description: Release a new version of the Digital Signature Confluence Data Center plugin. Drives the gated flow — unit test + build, upload to int-confluence.baloisenet.com, smoke test (rendering + signing) on the sandbox, then deploy to production confluence.baloisenet.com and smoke test there. Use when asked to release/deploy/ship a new DC plugin version.
---

# Release the Digital Signature DC plugin

Ship a new version through integration first, gate on its smoke test, then production.
**Never deploy to production before the integration smoke test passes.** Production
(`confluence.baloisenet.com`) is the live corporate wiki — confirm with the user before the
prod upload step.

Hosts (same admin `admin_b028178`, creds in `$ATLAS_BALOISE_NET_COM_ADMIN_USR`/`_PWD`):
- INT  `https://int-confluence.baloisenet.com`
- PROD `https://confluence.baloisenet.com`
- Sandbox page (both): `1383204089` — "Digital Signature Sandbox".

Scripts: `scripts/release-dc.sh` (test/build/upload/verify) and `scripts/smoke-test.sh`
(render + sign on the sandbox). Plugin key `com.baloise.confluence.digital-signature`;
version comes from `pom.xml` `<version>`.

## Flow

1. **Decide the version.** Confirm/bump `<version>` in `pom.xml` if this is a new release
   (the prod instance is Confluence 9.x → use the default v9 build).

2. **Unit test + build.**
   ```bash
   scripts/release-dc.sh test       # mvn clean test — must be green
   scripts/release-dc.sh build      # mvn clean package -DskipTests -> staging/
   scripts/release-dc.sh jar        # confirm the JAR it will upload
   ```

3. **Upload to INT, then verify enabled.**
   ```bash
   scripts/release-dc.sh upload https://int-confluence.baloisenet.com
   ```
   (Gets a UPM token, POSTs the JAR, polls until the plugin reports `enabled: true` and
   prints the installed version. Requires sysadmin rights — `admin_b028178` has them.)

4. **Smoke test on INT.** Confirm rendering + signing on the sandbox:
   ```bash
   scripts/smoke-test.sh https://int-confluence.baloisenet.com 1383204089
   ```
   Must end with `SMOKE PASS`. If it fails, STOP — fix and re-iterate from step 2. Do not
   proceed to prod.

5. **Deploy to PROD** (gate: confirm with the user first).
   ```bash
   scripts/release-dc.sh upload https://confluence.baloisenet.com
   ```

6. **Smoke test on PROD.**
   ```bash
   scripts/smoke-test.sh https://confluence.baloisenet.com 1383204089
   ```
   Must end with `SMOKE PASS`. Report the installed version on both hosts and the smoke
   results back to the user.

## What the smoke test asserts

- **Rendering:** the sandbox page's `body.view` contains the signature macro's export
  link(s) — proves the macro executes (plugin loaded, OSGi/spring-scanner wiring intact).
- **Signing:** every `GET /rest/signature/1.0/sign?key=…` returns **307** (REST resource
  live) and at least one macro records the signer (admin_b028178 then appears in
  `GET /rest/signature/1.0/export?key=…`) — proves the Bandana write path works end-to-end.
  A macro that returns 307 but doesn't record just means admin isn't a signer for that
  contract (not petition mode) — reported as `[ OK ]`, not a failure. The sandbox has a mix
  of macro configs by design.

## Notes / pitfalls

- UPM upload is **asynchronous**; `release-dc.sh upload` already polls for `enabled`. A 200
  on the POST alone is not success.
- If the UPM token request fails, the account lacks sysadmin or basic-auth/UPM upload is
  restricted on that host — surface the error, don't retry blindly.
- The signature-macro REST resources need `@ConfluenceComponent` + `@ComponentImport` under
  Confluence 9 spring-scanner, or they 500 at runtime (see the repo memory). The smoke test
  catches this — a 500 on `/sign` or a missing export link means a wiring regression.
- For deeper post-deploy inspection (Bandana state, etc.) use the `scriptrunner-exec` skill.
