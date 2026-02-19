# Cloud Dependencies BOM

Bill of Materials (BOM) for cloud storage SDK dependencies used across the geospatial Java ecosystem.

This BOM provides a single source of truth for cloud storage SDK versions, Netty exclusions, and dependency convergence overrides, consumed by:

- [tileverse](https://github.com/tileverse-io/tileverse) — Cloud-optimized geospatial data access libraries
- [imageio-ext](https://github.com/geosolutions-it/imageio-ext) — GeoTools/GeoServer image I/O extensions
- [GeoTools](https://github.com/geotools/geotools) — Java geospatial toolkit
- [GeoWebCache](https://github.com/GeoWebCache/geowebcache) — Tile caching server
- [GeoServer](https://github.com/geoserver/geoserver) — Open source geospatial server

## Maven Coordinates

```xml
<dependencyManagement>
  <dependencies>
    <dependency>
      <groupId>io.tileverse</groupId>
      <artifactId>cloud-dependencies-bom</artifactId>
      <version>1.0-SNAPSHOT</version>
      <type>pom</type>
      <scope>import</scope>
    </dependency>
  </dependencies>
</dependencyManagement>
```

For snapshot versions, add the Central Portal Snapshots repository:

```xml
<repositories>
  <repository>
    <id>central-portal-snapshots</id>
    <url>https://central.sonatype.com/repository/maven-snapshots/</url>
    <releases><enabled>false</enabled></releases>
    <snapshots><enabled>true</enabled></snapshots>
  </repository>
</repositories>
```

## Managed Dependencies

### Azure

| Artifact | Version | Notes |
|----------|---------|-------|
| `com.azure:azure-storage-blob` | 12.32.0 | Netty excluded |
| `com.azure:azure-core-http-jdk-httpclient` | 1.1.2 | JDK HttpClient replacement for Netty |
| `com.azure:azure-identity` | 1.18.1 | Netty excluded |

### AWS

| Artifact | Version | Notes |
|----------|---------|-------|
| `software.amazon.awssdk:s3` | 2.41.31 | Netty and Apache HTTP excluded |
| `software.amazon.awssdk:aws-crt-client` | 2.41.31 | CRT-based replacement for Netty |
| `software.amazon.awssdk:auth` | 2.41.31 | |
| `software.amazon.awssdk:sts` | 2.41.31 | Netty and Apache HTTP excluded |
| `software.amazon.awssdk:sso` | 2.41.31 | Netty and Apache HTTP excluded |

### Google Cloud Storage

| Artifact | Version | Notes |
|----------|---------|-------|
| `com.google.cloud:google-cloud-storage-bom` | 2.63.0 | Imported BOM |

### Imported BOMs

| BOM | Version | Notes |
|-----|---------|-------|
| `com.fasterxml.jackson:jackson-bom` | 2.20.0 | Azure convergence fix |
| `com.google.cloud:google-cloud-storage-bom` | 2.63.0 | GCS and transitive deps |

### Dependency Convergence Overrides

| Artifact | Version | Reason |
|----------|---------|--------|
| `com.google.errorprone:error_prone_annotations` | 2.45.0 | caffeine vs google-cloud-storage conflict |
| `net.java.dev.jna:jna` | 5.18.1 | azure-identity transitive conflict |
| `net.java.dev.jna:jna-platform` | 5.18.1 | azure-identity transitive conflict |
| `com.microsoft.azure:msal4j` | 1.23.1 | azure-identity vs msal4j-persistence-extension conflict |
| `org.slf4j:slf4j-api` | 2.0.16 | azure/aws (1.7.x) vs google-cloud-storage (2.0.x) conflict |

## Netty Exclusion Strategy

Both the AWS and Azure SDKs default to Netty as their HTTP transport. This BOM systematically excludes Netty and replaces it with lighter alternatives. The reasons are:

1. **Massive transitive dependency tree** — Netty pulls in 15+ JARs (`netty-buffer`, `netty-codec`, `netty-handler`, `netty-transport`, native epoll/kqueue modules, etc.), adding significant weight to the classpath for what amounts to an HTTP client.

2. **Version conflicts** — The Azure SDK and AWS SDK often depend on different Netty versions. In applications that use both (e.g., GeoServer with S3 and Azure blob stores), this causes dependency convergence failures that are difficult to resolve.

3. **Redundant in server environments** — Applications like GeoServer already run inside a servlet container (Jetty/Tomcat) that provides HTTP server capabilities. Netty's async I/O model provides no benefit when the cloud SDKs are used as clients making blocking calls from request-handling threads.

4. **Native library complications** — Netty includes platform-specific native transports (`netty-transport-native-epoll`, `netty-transport-native-kqueue`) that can cause `UnsatisfiedLinkError` in constrained environments (containers, certain CI systems).

### Replacements

- **Azure**: Excludes `azure-core-http-netty`, replaced by `azure-core-http-jdk-httpclient` which uses Java's built-in `java.net.http.HttpClient` (available since Java 11). Zero additional dependencies.

- **AWS**: Excludes `netty-nio-client` and `apache-client`, replaced by `aws-crt-client` (AWS Common Runtime). The CRT client provides both sync (`AwsCrtHttpClient`) and async (`AwsCrtAsyncHttpClient`) alternatives with improved S3 transfer reliability — it retries individual failed parts of a multipart transfer without restarting from the beginning, and includes enhanced connection pooling and DNS load balancing.

## Verification Module

The `verification/` submodule exists to make the `dependencyConvergence` enforcer rule actually work on this BOM.

A BOM only has `<dependencyManagement>` — no real `<dependencies>`. The enforcer's `dependencyConvergence` rule checks the *resolved* dependency tree, so it silently passes on the BOM itself since there's nothing to resolve. The verification module declares actual dependencies on all managed cloud SDK artifacts, giving the enforcer a real dependency tree to check for transitive version conflicts.

The module is:
- **Not published** — excluded from `central-publishing-maven-plugin` via `<excludeArtifacts>` and skips install/deploy
- **Invisible to consumers** — `flattenMode=ossrh` strips `<modules>` from the deployed POM
- **Automatically checked** — inherits the `dependencyConvergence` profile from the parent, so `make install` and `make lint` both verify convergence

## Versioning

This project uses [Maven CI-friendly versioning](https://maven.apache.org/maven-ci-friendly.html). The version is defined by a single `${revision}` property in `pom.xml` (default: `1.0-SNAPSHOT`), and the `flatten-maven-plugin` resolves it to a concrete value in the published POM.

- **Snapshots**: Published automatically on every push to `main` using the default `${revision}` value (e.g., `1.0-SNAPSHOT`).
- **Releases**: The version is overridden at build time by passing `-Drevision=<version>` to Maven. The `publish-release.yml` workflow does this automatically by extracting the version from the git tag (e.g., tag `v1.0.0` sets `-Drevision=1.0.0`).

To bump the snapshot version (e.g., after releasing 1.0.0), simply update the `<revision>` property in `pom.xml` to the next development version (e.g., `1.1-SNAPSHOT`).

## Release Guide

### 1. Update Dependency Versions

1. Edit the `<properties>` section in `pom.xml` with the new SDK versions
2. Run `make format` to sort the POM
3. Run `make lint` to verify formatting
4. Run `make install` to validate the BOM installs correctly
5. Inspect `.flattened-pom.xml` to verify all properties are resolved

### 2. Update the README

Update the version tables in the [Managed Dependencies](#managed-dependencies) section to reflect the new versions. This keeps the README as a quick reference without having to open `pom.xml`.

### 3. Merge to Main

Open a pull request with the version updates. Once CI passes and the PR is merged, a snapshot is automatically published to Maven Central.

### 4. Tag and Publish

1. Create and push a tag: `git tag v1.0.0 && git push origin v1.0.0`
2. The `publish-release.yml` workflow will automatically:
   - Validate the POM
   - Sign and deploy to Maven Central
   - Create a GitHub Release

Alternatively, use the workflow dispatch: Actions > Publish Release > Run workflow, and enter the version (e.g., `1.0.0`).

### 5. Bump Snapshot Version

After releasing, update the `<revision>` property in `pom.xml` to the next development version (e.g., `1.1-SNAPSHOT`) and merge to `main`.

### GitHub Secrets

The publishing workflows use `GPG_PRIVATE_KEY`, `GPG_PASSPHRASE`, `CENTRAL_USERNAME`, and `CENTRAL_TOKEN`. These are configured as organization-level secrets on `tileverse-io` and are available to all repositories in the organization — no per-repo setup needed.

## Local Development

```bash
# Sort POM file
make format

# Check POM formatting
make lint

# Install BOM to local Maven repository
make install

# Full verification (lint + install)
make verify

# Show project information
make info

# Clean build artifacts
make clean
```

## License

[Apache License 2.0](LICENSE)
