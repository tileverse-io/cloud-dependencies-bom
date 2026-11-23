# AGENTS.md

Notes for coding agents working on this BOM. Read the [README](README.md) first, in particular the Release Guide.

## Checks

Run `make verify` after each change. It checks the POM formatting, the dependency convergence of the `verification` module, and the enforcer bans on Netty, the gRPC transport and the Apache HTTP clients.

## Rules not checked by the build

- A version change also updates the tables in the README's [Managed Dependencies](README.md#managed-dependencies) section.
- The four Azure artifacts pinned in `pom.xml` must have the versions managed by `azure-sdk-bom`. Their entries exist to exclude `azure-core-http-netty`, and Maven requires a version on them: an entry without one breaks the projects importing this BOM.
- `opentelemetry.version` and `grpc.version` must match the versions used by `google-cloud-storage`.
- A convergence pin must not downgrade a library. Run `./mvnw dependency:tree -Dverbose -pl verification` and read the `version managed from` notes. A line like `msal4j:jar:1.23.1 (version managed from 1.26.0)` is a downgrade: the pin replaced 1.26.0 with an older version.
- Spring Boot projects such as GeoServer Cloud import this BOM before `spring-boot-dependencies`. Spring Boot's entries still win for the transitive libraries not managed here. Before a release, resolve the `verification` dependencies with both BOMs imported in that order, and look for downgrades the same way.

## Commits

Dependency commits list each change as `- group:artifact:old (release date) -> new (release date)`, then a short paragraph for each fact the diff cannot explain. See `git log` for examples.
