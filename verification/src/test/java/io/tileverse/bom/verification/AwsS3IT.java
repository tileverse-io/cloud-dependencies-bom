/*
 * Copyright (c) 2026 Multiversio LLC.
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.tileverse.bom.verification;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.localstack.LocalStackContainer;
import org.testcontainers.utility.DockerImageName;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.async.AsyncRequestBody;
import software.amazon.awssdk.core.async.AsyncResponseTransformer;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.http.crt.AwsCrtAsyncHttpClient;
import software.amazon.awssdk.http.crt.AwsCrtHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3AsyncClient;
import software.amazon.awssdk.services.s3.S3AsyncClientBuilder;
import software.amazon.awssdk.services.s3.S3BaseClientBuilder;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3ClientBuilder;
import software.amazon.awssdk.services.s3.S3CrtAsyncClientBuilder;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response;
import software.amazon.awssdk.services.s3.model.S3Object;
import software.amazon.awssdk.services.sts.StsClient;
import software.amazon.awssdk.services.sts.StsClientBuilder;
import software.amazon.awssdk.services.sts.model.AssumeRoleResponse;

/** S3 and STS clients against LocalStack, over the AWS CRT transports kept by the BOM. */
@Testcontainers
class AwsS3IT {

    // LocalStack images released since March 2026 require an auth token
    @Container
    static final LocalStackContainer LOCALSTACK =
            new LocalStackContainer(DockerImageName.parse("localstack/localstack:3.2.0")).withServices("s3", "sts");

    private static final String ROLE_ARN = "arn:aws:iam::000000000000:role/verification";

    @Test
    void crtSyncClientRoundTrip() {
        S3ClientBuilder builder = configure(S3Client.builder()).httpClientBuilder(AwsCrtHttpClient.builder());
        try (S3Client s3 = builder.build()) {
            assertRoundTrip(s3, "crt-sync");
        }
    }

    // the SDK must find the CRT transport by itself, the Apache transports being excluded
    @Test
    void defaultSyncClientRoundTrip() {
        try (S3Client s3 = configure(S3Client.builder()).build()) {
            assertRoundTrip(s3, "default-sync");
        }
    }

    @Test
    void crtAsyncClientRoundTrip() {
        S3AsyncClientBuilder builder =
                configure(S3AsyncClient.builder()).httpClientBuilder(AwsCrtAsyncHttpClient.builder());
        try (S3AsyncClient s3 = builder.build()) {
            assertRoundTrip(s3, "crt-async");
        }
    }

    // the AWS CRT-based S3 client, recommended by the BOM for transfers
    @Test
    void crtS3ClientRoundTrip() {
        S3CrtAsyncClientBuilder builder = S3AsyncClient.crtBuilder()
                .endpointOverride(LOCALSTACK.getEndpoint())
                .forcePathStyle(true)
                .region(region())
                .credentialsProvider(credentials());
        try (S3AsyncClient s3 = builder.build()) {
            assertRoundTrip(s3, "crt-s3");
        }
    }

    @Test
    void stsAssumeRoleOverCrt() {
        StsClientBuilder builder = StsClient.builder()
                .endpointOverride(LOCALSTACK.getEndpoint())
                .region(region())
                .credentialsProvider(credentials())
                .httpClientBuilder(AwsCrtHttpClient.builder());
        try (StsClient sts = builder.build()) {
            AssumeRoleResponse response =
                    sts.assumeRole(request -> request.roleArn(ROLE_ARN).roleSessionName("verification"));
            assertThat(response.credentials().accessKeyId()).isNotBlank();
        }
    }

    private static <B extends S3BaseClientBuilder<B, ?>> B configure(B builder) {
        return builder.endpointOverride(LOCALSTACK.getEndpoint())
                .region(region())
                .credentialsProvider(credentials())
                .forcePathStyle(true);
    }

    private static Region region() {
        return Region.of(LOCALSTACK.getRegion());
    }

    private static AwsCredentialsProvider credentials() {
        AwsBasicCredentials credentials =
                AwsBasicCredentials.create(LOCALSTACK.getAccessKey(), LOCALSTACK.getSecretKey());
        return StaticCredentialsProvider.create(credentials);
    }

    private static void assertRoundTrip(S3Client s3, String bucket) {
        s3.createBucket(request -> request.bucket(bucket));
        s3.putObject(request -> request.bucket(bucket).key(Sample.KEY), RequestBody.fromString(Sample.CONTENT));

        assertThat(readRange(s3, bucket)).isEqualTo(Sample.RANGE_CONTENT);
        assertThat(listKeys(s3, bucket)).containsExactly(Sample.KEY);

        s3.deleteObject(request -> request.bucket(bucket).key(Sample.KEY));
        s3.deleteBucket(request -> request.bucket(bucket));
    }

    private static void assertRoundTrip(S3AsyncClient s3, String bucket) {
        s3.createBucket(request -> request.bucket(bucket)).join();
        s3.putObject(request -> request.bucket(bucket).key(Sample.KEY), AsyncRequestBody.fromString(Sample.CONTENT))
                .join();

        assertThat(readRange(s3, bucket)).isEqualTo(Sample.RANGE_CONTENT);
        assertThat(listKeys(s3, bucket)).containsExactly(Sample.KEY);

        s3.deleteObject(request -> request.bucket(bucket).key(Sample.KEY)).join();
        s3.deleteBucket(request -> request.bucket(bucket)).join();
    }

    private static String readRange(S3Client s3, String bucket) {
        ResponseBytes<GetObjectResponse> bytes = s3.getObjectAsBytes(rangeRequest(bucket));
        return bytes.asUtf8String();
    }

    private static String readRange(S3AsyncClient s3, String bucket) {
        ResponseBytes<GetObjectResponse> bytes =
                s3.getObject(rangeRequest(bucket), AsyncResponseTransformer.toBytes()).join();
        return bytes.asUtf8String();
    }

    private static GetObjectRequest rangeRequest(String bucket) {
        int lastByte = Sample.RANGE_OFFSET + Sample.RANGE_LENGTH - 1;
        String range = "bytes=" + Sample.RANGE_OFFSET + "-" + lastByte;
        return GetObjectRequest.builder().bucket(bucket).key(Sample.KEY).range(range).build();
    }

    private static List<String> listKeys(S3Client s3, String bucket) {
        ListObjectsV2Response response = s3.listObjectsV2(request -> request.bucket(bucket));
        return keys(response);
    }

    private static List<String> listKeys(S3AsyncClient s3, String bucket) {
        ListObjectsV2Response response =
                s3.listObjectsV2(request -> request.bucket(bucket)).join();
        return keys(response);
    }

    private static List<String> keys(ListObjectsV2Response response) {
        return response.contents().stream().map(S3Object::key).toList();
    }
}
