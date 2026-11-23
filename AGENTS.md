# AGENTS.md

Notes for coding agents working on this BOM. Read the [README](README.md) first, in particular the Release Guide and the Verification Module section.

## Checks

Run `make verify` after each change. It checks the POM formatting and the dependency convergence, applies the enforcer bans on Netty, the gRPC transport and the Apache HTTP clients, and runs the tests of the `verification` modules against storage emulators. The tests need Docker.

## Rules not checked by the build

- A version change also updates the tables in the README's [Managed Dependencies](README.md#managed-dependencies) section.
- A newly managed artifact also gets a dependency in `verification/pom.xml` and a test in `verification/src/test/java`.
- The four Azure artifacts pinned in `pom.xml` must have the versions managed by `azure-sdk-bom`. Their entries exist to exclude `azure-core-http-netty`, and Maven requires a version on them: an entry without one breaks the projects importing this BOM.
- `opentelemetry.version` and `grpc.version` must match the versions used by `google-cloud-storage`.
- A convergence pin must not downgrade a library. Run `./mvnw dependency:tree -Dverbose -pl verification/standalone` and read the `version managed from` notes. A line like `msal4j:jar:1.23.1 (version managed from 1.26.0)` is a downgrade: the pin replaced 1.26.0 with an older version.
- `verification/spring-boot` imports Spring Boot's BOM after this one, as GeoServer Cloud does. Keep its `spring-boot.version` in line with GeoServer Cloud. Its tests fail on a downgrade only when the older version breaks one of their calls: read its `dependency:tree -Dverbose` output for downgrades too.
- An Azure SDK upgrade can require a newer Azurite image in `AzureBlobIT`: Azurite rejects service versions newer than its own.

## Commits

Dependency commits list each change as `- group:artifact:old (release date) -> new (release date)`, then a short paragraph for each fact the diff cannot explain. See `git log` for examples.
