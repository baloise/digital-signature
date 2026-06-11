# Follow-up: scan-indexes approach fails silently on Confluence 9.5.4

Thanks for the detailed answer! I tried the `scan-indexes` approach but the plugin fails to enable (hangs during startup with no errors in logs).

## What I tried

1. Replaced `plugin-context.xml` with:
```xml
<beans xmlns:atlassian-scanner="http://www.atlassian.com/schema/atlassian-scanner/2" ...>
    <atlassian-scanner:scan-indexes/>
</beans>
```

2. Created a `ComponentImporter` class with `@ComponentImport` fields for all 9 OSGi services (BandanaManager, UserManager, etc.)

3. Migration listener annotated with `@ExportAsService` + `@Component`

4. Macro and REST classes have `@ComponentImport` on constructor params but NO `@Component`/`@ConfluenceComponent`

5. Dependencies: `atlassian-spring-scanner-annotation` 5.1.0 (provided) + `atlassian-spring-scanner-runtime` 5.1.0 (runtime)

## Result

Plugin fails to enable — same `WaitUntil.invoke` → `Thread.sleep` hang pattern with zero log output. I tested progressively:
- `scan-indexes` + `@ExportAsService` + `@Component` → fails
- `scan-indexes` + `@Component` only (no `@ExportAsService`) → still fails  
- `scan-indexes` alone (no annotated migration listener) → still fails

So the issue is `scan-indexes` itself, not `@ExportAsService`.

## My suspicion

The Spring Scanner runtime (5.1.0) introduces new mandatory imports (`org.springframework.beans.factory`, `org.springframework.stereotype`, etc.) that may not be exported by Confluence 9.5.4's OSGi framework. I added `org.springframework.*;resolution:="optional"` to Import-Package but it still fails.

## Questions

1. What version of `atlassian-spring-scanner` are you using? Is it 5.x or an older 2.x version?
2. Do you need `atlassian-spring-scanner-processor` or another compile-time artifact to generate the annotation index?
3. What Confluence Server version do you test against?

## Working fallback

With the original manual `plugin-context.xml` (OSGi Blueprint XML), the plugin works perfectly — the migration listener bean is created, the macro renders, signatures are stored. The only missing piece is the OSGi service registration for CMA discovery.
