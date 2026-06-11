# How to register DiscoverableForgeListener for CMA app migration in a plugin with plugin-context.xml?

**Category:** Cloud Migrations / App Migration Platform
**Product:** Confluence Server/DC → Cloud (Forge)

## Summary

I'm implementing the [App Migration Platform](https://developer.atlassian.com/platform/app-migration/prepare-server-app-forge/) for my Confluence Server plugin to migrate app data to a Forge cloud app via CMA. My plugin uses a hand-written `plugin-context.xml` for Spring bean wiring (not pure Spring Scanner annotations). I cannot get CMA to discover my `DiscoverableForgeListener` implementation.

## Setup

- Confluence Server 9.5.4
- `atlassian-app-cloud-migration-listener` 1.8.7
- `atlassian-spring-scanner-annotation` 5.1.0
- CMA (migration-agent) 3.13.8
- Dev mode enabled

My migration listener class:

```java
@ConfluenceComponent
@ExportAsService({DiscoverableForgeListener.class, ConfluenceAppCloudMigrationListenerV1.class})
public class DigitalSignatureMigrationListener
    implements DiscoverableForgeListener, ConfluenceAppCloudMigrationListenerV1 {

    public DigitalSignatureMigrationListener(@ComponentImport BandanaManager bandanaManager) {
        this.bandanaManager = bandanaManager;
    }
    // ... getForgeAppId(), onStartAppMigration(), etc.
}
```

## Problem

My plugin has two other beans (`DigitalSignatureMacro` and `DigitalSignatureService`) that are defined in `META-INF/spring/plugin-context.xml` using manual Spring XML wiring. Because this file exists, **Spring Scanner does not process the `@ExportAsService` annotation** on my migration listener — so the bean is never registered as an OSGi service, and CMA shows `appStatus: NO_APPS`.

## What I've tried

| Approach | Result |
|----------|--------|
| `@ExportAsService` + `@ConfluenceComponent` | Annotations ignored because `plugin-context.xml` exists |
| Delete `plugin-context.xml`, use annotations everywhere | Plugin starts but macro/REST beans break (HK2 conflict) |
| `<osgi:service>` in `plugin-context.xml` | Plugin won't start — CCMA doesn't export `com.atlassian.migration.app.listener` or `.confluence` sub-packages, only the flat `com.atlassian.migration.app` |
| `<component>` in `atlassian-plugin.xml` | Build fails: "not allowed when Atlassian-Plugin-Key is set" |
| `<scanner:scan-packages>` in a second Spring XML file | Plugin won't start — duplicate bean conflicts |
| Programmatic `BundleContext.registerService()` from constructor | Plugin starts, service registered, but CMA can't find it (classloader mismatch — our bundled interfaces vs CCMA's interfaces) |
| Bean-only in `plugin-context.xml` (no service export) | Plugin works perfectly, but CMA shows NO_APPS |

## Key observation

CCMA (bundle 272) exports `com.atlassian.migration.app` version 1.0.0, but does **NOT** export:
- `com.atlassian.migration.app.listener` (where `DiscoverableForgeListener` lives)
- `com.atlassian.migration.app.confluence` (where `ConfluenceAppCloudMigrationListenerV1` lives)
- `com.atlassian.migration.app.gateway` (where `AppCloudForgeMigrationGateway` lives)

This means the CMA listener dependency must be bundled (compile scope), which creates a split-package situation — our bundle's classloader has different class instances than CCMA's classloader, so OSGi service lookups never match.

## Questions

1. **How do other Marketplace vendors register their `DiscoverableForgeListener`?** Is there something I'm missing about the service discovery mechanism?

2. **Should the CMA listener interfaces be imported from CCMA or bundled?** The [documentation](https://developer.atlassian.com/platform/app-migration/prepare-server-app-forge/) says "the Cloud Migration Assistants export the library's classes in the runtime" — but in practice, the sub-packages are not exported.

3. **Is there a way to use `plugin-context.xml` alongside Spring Scanner's `@ExportAsService`?** My plugin's macro and REST beans require manual XML wiring, but the migration listener needs annotation-based service export.

## Environment details

- `pom.xml` CMA dependency: `<scope>provided</scope>` (also tried compile with `!com.atlassian.migration.app.*` in Import-Package)
- OSGi manifest: `Spring-Context: *`
- Plugin key: `com.baloise.confluence.digital-signature`
- Plugin is open source: https://github.com/baloise/digital-signature
