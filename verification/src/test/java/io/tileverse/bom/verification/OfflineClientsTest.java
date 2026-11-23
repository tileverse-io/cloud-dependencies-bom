/*
 * Copyright (c) 2026 Multiversio LLC.
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.tileverse.bom.verification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.azure.core.credential.TokenRequestContext;
import com.azure.identity.ClientSecretCredential;
import com.azure.identity.ClientSecretCredentialBuilder;
import com.azure.storage.common.StorageSharedKeyCredential;
import com.azure.storage.file.datalake.DataLakeFileClient;
import com.azure.storage.file.datalake.DataLakeServiceClient;
import com.azure.storage.file.datalake.DataLakeServiceClientBuilder;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.auth.credentials.AnonymousCredentialsProvider;
import software.amazon.awssdk.http.crt.AwsCrtHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sso.SsoClient;
import software.amazon.awssdk.services.sso.SsoClientBuilder;

/**
 * Clients and credentials not served by the emulators: Azurite has no Data Lake endpoint, and there is no identity
 * provider to sign in to. Building and calling them still loads their classes, where a missing or incompatible
 * dependency shows up as a {@link LinkageError}.
 */
class OfflineClientsTest {

    private static final String DATA_LAKE_ENDPOINT = "https://verification.dfs.core.windows.net";

    private static final String UNREACHABLE_AUTHORITY = "https://localhost:1";

    @Test
    void dataLakeClientBuilds() {
        String accountKey = Base64.getEncoder().encodeToString("verification".getBytes(StandardCharsets.UTF_8));
        StorageSharedKeyCredential credential = new StorageSharedKeyCredential("verification", accountKey);
        DataLakeServiceClient service = new DataLakeServiceClientBuilder()
                .endpoint(DATA_LAKE_ENDPOINT)
                .credential(credential)
                .buildClient();

        DataLakeFileClient file = service.getFileSystemClient("filesystem").getFileClient("file.txt");

        assertThat(file.getFileUrl()).isEqualTo(DATA_LAKE_ENDPOINT + "/filesystem/file.txt");
    }

    // a LinkageError is an Error, not an Exception
    @Test
    void azureSecretFailsCleanly() {
        ClientSecretCredential credential = new ClientSecretCredentialBuilder()
                .tenantId("00000000-0000-0000-0000-000000000000")
                .clientId("verification")
                .clientSecret("verification")
                .authorityHost(UNREACHABLE_AUTHORITY)
                .disableInstanceDiscovery()
                .build();
        TokenRequestContext request = new TokenRequestContext().addScopes("https://storage.azure.com/.default");

        assertThatThrownBy(() -> credential.getTokenSync(request)).isInstanceOf(Exception.class);
    }

    @Test
    void ssoClientBuilds() {
        SsoClientBuilder builder = SsoClient.builder()
                .region(Region.US_EAST_1)
                .credentialsProvider(AnonymousCredentialsProvider.create())
                .httpClientBuilder(AwsCrtHttpClient.builder());

        try (SsoClient sso = builder.build()) {
            assertThat(sso.serviceClientConfiguration().region()).isEqualTo(Region.US_EAST_1);
        }
    }
}
