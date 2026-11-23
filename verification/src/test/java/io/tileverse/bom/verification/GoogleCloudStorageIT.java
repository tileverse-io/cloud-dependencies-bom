/*
 * Copyright (c) 2026 Multiversio LLC.
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.tileverse.bom.verification;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.api.gax.paging.Page;
import com.google.cloud.NoCredentials;
import com.google.cloud.ReadChannel;
import com.google.cloud.storage.Blob;
import com.google.cloud.storage.BlobId;
import com.google.cloud.storage.BlobInfo;
import com.google.cloud.storage.BucketInfo;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageOptions;
import io.aiven.testcontainers.fakegcsserver.FakeGcsServerContainer;
import java.io.IOException;
import java.nio.channels.Channels;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Google Cloud Storage client against fake-gcs-server, over the HTTP/JSON transport. The BOM excludes the gRPC
 * transport, and this test fails if the exclusions remove a class needed by the HTTP/JSON path.
 */
@Testcontainers
class GoogleCloudStorageIT {

    @Container
    static final FakeGcsServerContainer GCS = new FakeGcsServerContainer();

    @Test
    void httpJsonRoundTrip() throws Exception {
        String bucket = "round-trip";
        BlobId blobId = BlobId.of(bucket, Sample.KEY);
        try (Storage storage = storage()) {
            storage.create(BucketInfo.of(bucket));
            storage.create(BlobInfo.newBuilder(blobId).build(), Sample.CONTENT.getBytes(StandardCharsets.UTF_8));

            assertThat(readRange(storage, blobId)).isEqualTo(Sample.RANGE_CONTENT);
            assertThat(listNames(storage, bucket)).containsExactly(Sample.KEY);

            storage.delete(blobId);
            storage.delete(bucket);
        }
    }

    private static Storage storage() {
        StorageOptions options = StorageOptions.http()
                .setHost(GCS.url())
                .setProjectId("verification")
                .setCredentials(NoCredentials.getInstance())
                .build();
        return options.getService();
    }

    private static String readRange(Storage storage, BlobId blobId) throws IOException {
        try (ReadChannel reader = storage.reader(blobId)) {
            reader.seek(Sample.RANGE_OFFSET);
            reader.limit(Sample.RANGE_OFFSET + Sample.RANGE_LENGTH);
            byte[] bytes = Channels.newInputStream(reader).readAllBytes();
            return new String(bytes, StandardCharsets.UTF_8);
        }
    }

    private static List<String> listNames(Storage storage, String bucket) {
        Page<Blob> blobs = storage.list(bucket);
        List<String> names = new ArrayList<>();
        for (Blob blob : blobs.iterateAll()) {
            names.add(blob.getName());
        }
        return names;
    }
}
