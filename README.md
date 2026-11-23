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
      <version>1.1-SNAPSHOT</version>
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
| `com.azure:azure-storage-blob` | 12.37.2 | Netty excluded |
| `com.azure:azure-storage-blob-batch` | 12.33.2 | Netty excluded |
| `com.azure:azure-storage-file-datalake` | 12.30.2 | Netty excluded |
| `com.azure:azure-core-http-jdk-httpclient` | (from BOM) | JDK HttpClient replacement for Netty |
| `com.azure:azure-identity` | 1.19.0 | Netty excluded |

### AWS

| Artifact | Version | Notes |
|----------|---------|-------|
| `software.amazon.awssdk:s3` | 2.59.7 | Netty and Apache HTTP excluded |
| `software.amazon.awssdk:aws-crt-client` | 2.59.7 | CRT-based replacement for Netty (version from AWS SDK BOM) |
| `software.amazon.awssdk:auth` | 2.59.7 | Version from AWS SDK BOM |
| `software.amazon.awssdk:sts` | 2.59.7 | Netty and Apache HTTP excluded |
| `software.amazon.awssdk:sso` | 2.59.7 | Netty and Apache HTTP excluded |

### Google Cloud Storage

| Artifact | Version | Notes |
|----------|---------|-------|
| `com.google.cloud:google-cloud-storage-bom` | 2.79.0 | Imported BOM |

### Imported BOMs

| BOM | Version | Notes |
|-----|---------|-------|
| `com.azure:azure-sdk-bom` | 1.3.10 | Azure SDK version management |
| `com.fasterxml.jackson:jackson-bom` | 2.23.0 | Azure convergence fix |
| `io.opentelemetry:opentelemetry-bom` | 1.67.0 | Version used by google-cloud-storage, newer than Spring Boot's |
| `io.grpc:grpc-bom` | 1.85.1 | Version used by google-cloud-storage, newer than Spring Boot's |
| `com.google.cloud:google-cloud-storage-bom` | 2.79.0 | GCS and transitive deps |
| `software.amazon.awssdk:bom` | 2.59.7 | AWS SDK version management |

### Dependency Convergence Overrides

| Artifact | Version | Reason |
|----------|---------|--------|
| `com.google.errorprone:error_prone_annotations` | 2.50.0 | caffeine vs google-cloud-storage conflict |
| `net.java.dev.jna:jna` | 5.19.1 | azure-identity transitive conflict |
| `net.java.dev.jna:jna-platform` | 5.19.1 | azure-identity transitive conflict |
| `com.microsoft.azure:msal4j` | 1.26.0 | azure-identity vs msal4j-persistence-extension conflict |
| `org.slf4j:slf4j-api` | 2.0.20 | azure/aws (1.7.x) vs google-cloud-storage (2.0.x) conflict |
| `org.jspecify:jspecify` | 1.0.1 | azure-core -> reactor-core vs google-cloud-storage conflict |

## Netty Exclusion Strategy

Both the AWS and Azure SDKs default to Netty as their HTTP transport. This BOM systematically excludes Netty and replaces it with lighter alternatives. The reasons are:

1. **Massive transitive dependency tree** — Netty pulls in 15+ JARs (`netty-buffer`, `netty-codec`, `netty-handler`, `netty-transport`, native epoll/kqueue modules, etc.), adding significant weight to the classpath for what amounts to an HTTP client.

2. **Version conflicts** — The Azure SDK and AWS SDK often depend on different Netty versions. In applications that use both (e.g., GeoServer with S3 and Azure blob stores), this causes dependency convergence failures that are difficult to resolve.

3. **Redundant in server environments** — Applications like GeoServer already run inside a servlet container (Jetty/Tomcat) that provides HTTP server capabilities. Netty's async I/O model provides no benefit when the cloud SDKs are used as clients making blocking calls from request-handling threads.

4. **Native library complications** — Netty includes platform-specific native transports (`netty-transport-native-epoll`, `netty-transport-native-kqueue`) that can cause `UnsatisfiedLinkError` in constrained environments (containers, certain CI systems).

### Replacements

- **Azure**: Excludes `azure-core-http-netty`, replaced by `azure-core-http-jdk-httpclient` which uses Java's built-in `java.net.http.HttpClient` (available since Java 11). Zero additional dependencies.

- **AWS**: Excludes `netty-nio-client`, `apache-client` and `apache5-client` (the Apache HttpClient 5 transport that AWS SDK 2.54+ ships as its default sync client), replaced by `aws-crt-client` (AWS Common Runtime). The CRT client provides both sync (`AwsCrtHttpClient`) and async (`AwsCrtAsyncHttpClient`) alternatives with improved S3 transfer reliability — it retries individual failed parts of a multipart transfer without restarting from the beginning, and includes enhanced connection pooling and DNS load balancing.

## Verification Module

The `verification/` modules check that the managed SDKs resolve without conflicts and work at runtime. A BOM has no dependencies of its own, and a build of the BOM alone checks nothing. These modules declare dependencies on all managed SDK artifacts and run tests against them.

Two modules run the same tests, kept in `verification/src/test/java`:

- `verification/standalone` uses this BOM alone, as GeoTools, GeoWebCache and imageio-ext do.
- `verification/spring-boot` imports this BOM, then `spring-boot-dependencies`, as GeoServer Cloud does. Spring Boot's entries still apply to the transitive libraries not managed here. Keep its `spring-boot.version` in line with GeoServer Cloud.

A round trip creates a bucket or container, writes an object, reads a byte range back, lists the objects and deletes them.

| Test | Checks |
|------|--------|
| `TransportsTest` | Netty, the gRPC transport and the AWS Apache transports are off the classpath, `io.grpc.Context` is kept, the AWS SDK finds the CRT transport and Azure finds the JDK HttpClient transport |
| `OfflineClientsTest` | The Data Lake and SSO clients build, and an Azure client secret credential fails with an exception rather than a linkage error |
| `AwsS3IT` | S3 round trips on LocalStack with the CRT sync, async and S3 clients and with the default transport, and an STS `AssumeRole` call |
| `AzureBlobIT` | A blob round trip and a batch delete on Azurite |
| `GoogleCloudStorageIT` | A round trip on fake-gcs-server over the HTTP/JSON transport |

The integration tests need Docker, and they fail when Docker is missing instead of being skipped. LocalStack stays on 3.2.0: its images released since March 2026 require an auth token. Azurite rejects service versions newer than its own, and an Azure SDK upgrade can require a newer Azurite image.

The tests catch missing classes and broken transports. For example, excluding `grpc-api` from `google-cloud-storage` fails `GoogleCloudStorageIT` with `NoClassDefFoundError: io/grpc/Context`. A downgrade forced by Spring Boot fails them only when the older version breaks a call made by the tests: with OpenTelemetry 1.62.0 and gRPC 1.84.1 instead of 1.67.0 and 1.85.1, they still pass.

The modules are:
- **Not published**: the publish workflows build the BOM alone (`--non-recursive`), because the signing and publishing plugins fail on modules without an artifact. The modules are also excluded from `central-publishing-maven-plugin` via `<excludeArtifacts>`, and they skip install and deploy
- **Invisible to consumers**: `flattenMode=ossrh` strips `<modules>` from the deployed POM
- **Checked for convergence**: they inherit the `dependencyConvergence` profile and the banned dependencies rule from the parent

## Versioning

This project uses [Maven CI-friendly versioning](https://maven.apache.org/maven-ci-friendly.html). The version is defined by a single `${revision}` property in `pom.xml` (default: `1.1-SNAPSHOT`), and the `flatten-maven-plugin` resolves it to a concrete value in the published POM.

- **Snapshots**: Published automatically on every push to `main` using the default `${revision}` value (e.g., `1.1-SNAPSHOT`).
- **Releases**: The version is overridden at build time by passing `-Drevision=<version>` to Maven. The `publish-release.yml` workflow does this automatically by extracting the version from the git tag (e.g., tag `v1.0.0` sets `-Drevision=1.0.0`).

To bump the snapshot version (e.g., after releasing 1.0.0), simply update the `<revision>` property in `pom.xml` to the next development version (e.g., `1.1-SNAPSHOT`).

## Release Guide

### 1. Update Dependency Versions

1. Edit the `<properties>` section in `pom.xml` with the new SDK versions
2. Run `make format` to sort the POM
3. Run `make lint` to verify formatting
4. Run `make verify` to check convergence, run the verification tests and install the BOM (needs Docker)
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

# Install BOM to local Maven repository, without tests
make install

# Run the verification tests (needs Docker)
make test

# Full verification: lint, tests and install (needs Docker)
make verify

# Show project information
make info

# Clean build artifacts
make clean
```

## License

[Apache License 2.0](LICENSE)
