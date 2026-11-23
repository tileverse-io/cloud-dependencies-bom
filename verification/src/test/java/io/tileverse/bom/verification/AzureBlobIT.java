/*
 * Copyright (c) 2026 Multiversio LLC.
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.tileverse.bom.verification;

import static org.assertj.core.api.Assertions.assertThat;

import com.azure.core.http.rest.Response;
import com.azure.core.util.BinaryData;
import com.azure.storage.blob.BlobClient;
import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.BlobServiceClient;
import com.azure.storage.blob.BlobServiceClientBuilder;
import com.azure.storage.blob.batch.BlobBatchClient;
import com.azure.storage.blob.batch.BlobBatchClientBuilder;
import com.azure.storage.blob.models.BlobItem;
import com.azure.storage.blob.models.BlobRange;
import com.azure.storage.blob.models.DeleteSnapshotsOptionType;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.testcontainers.azure.AzuriteContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Blob and blob batch clients against Azurite, over the JDK HttpClient transport kept by the BOM. Azurite rejects the
 * service versions newer than its own: an Azure SDK upgrade can require a newer Azurite image.
 */
@Testcontainers
class AzureBlobIT {

    @Container
    static final AzuriteContainer AZURITE = new AzuriteContainer("mcr.microsoft.com/azure-storage/azurite:3.38.0");

    private static final int ACCEPTED = 202;

    @Test
    void blobRoundTrip() throws IOException {
        BlobContainerClient container = service().createBlobContainer("round-trip");
        BlobClient blob = container.getBlobClient(Sample.KEY);
        blob.upload(BinaryData.fromString(Sample.CONTENT));

        assertThat(readRange(blob)).isEqualTo(Sample.RANGE_CONTENT);
        assertThat(listNames(container)).containsExactly(Sample.KEY);

        blob.delete();
        container.delete();
    }

    @Test
    void batchDeletesBlobs() {
        BlobServiceClient service = service();
        BlobContainerClient container = service.createBlobContainer("batch");
        List<String> blobUrls = upload(container, "first.txt", "second.txt");
        BlobBatchClient batch = new BlobBatchClientBuilder(service).buildClient();

        for (Response<Void> response : batch.deleteBlobs(blobUrls, DeleteSnapshotsOptionType.INCLUDE)) {
            assertThat(response.getStatusCode()).isEqualTo(ACCEPTED);
        }
        assertThat(listNames(container)).isEmpty();

        container.delete();
    }

    private static BlobServiceClient service() {
        return new BlobServiceClientBuilder()
                .connectionString(AZURITE.getConnectionString())
                .buildClient();
    }

    private static String readRange(BlobClient blob) throws IOException {
        BlobRange range = new BlobRange(Sample.RANGE_OFFSET, (long) Sample.RANGE_LENGTH);
        try (InputStream in = blob.openInputStream(range, null)) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static List<String> upload(BlobContainerClient container, String... names) {
        List<String> blobUrls = new ArrayList<>();
        for (String name : names) {
            BlobClient blob = container.getBlobClient(name);
            blob.upload(BinaryData.fromString(Sample.CONTENT));
            blobUrls.add(blob.getBlobUrl());
        }
        return blobUrls;
    }

    private static List<String> listNames(BlobContainerClient container) {
        return container.listBlobs().stream().map(BlobItem::getName).toList();
    }
}
