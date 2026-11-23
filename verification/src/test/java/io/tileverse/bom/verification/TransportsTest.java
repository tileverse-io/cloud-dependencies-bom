/*
 * Copyright (c) 2026 Multiversio LLC.
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.tileverse.bom.verification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.azure.core.http.HttpClient;
import java.util.List;
import java.util.ServiceLoader;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.http.SdkHttpService;
import software.amazon.awssdk.http.async.SdkAsyncHttpService;

/**
 * The HTTP transports excluded by the BOM stay off the classpath, and each SDK finds the transport meant to replace
 * them.
 */
class TransportsTest {

    private static final String AWS_CRT_SERVICE = "software.amazon.awssdk.http.crt.AwsCrtSdkHttpService";

    private static final String AZURE_JDK_CLIENT = "com.azure.core.http.jdk.httpclient.JdkHttpClient";

    @Test
    void nettyIsExcluded() {
        assertMissing("io.netty.channel.Channel");
    }

    @Test
    void grpcTransportIsExcluded() {
        assertMissing("io.grpc.internal.ManagedChannelImplBuilder");
        assertMissing("io.grpc.netty.shaded.io.grpc.netty.NettyChannelBuilder");
    }

    // the GCS HTTP/JSON transport uses io.grpc.Context, from grpc-api
    @Test
    void grpcContextIsKept() {
        assertPresent("io.grpc.Context");
    }

    @Test
    void awsApacheClientsAreExcluded() {
        assertMissing("software.amazon.awssdk.http.apache.ApacheHttpClient");
        assertMissing("software.amazon.awssdk.http.apache5.Apache5HttpClient");
    }

    @Test
    void awsSyncTransportIsCrt() {
        assertThat(providers(SdkHttpService.class)).containsExactly(AWS_CRT_SERVICE);
    }

    @Test
    void awsAsyncTransportIsCrt() {
        assertThat(providers(SdkAsyncHttpService.class)).containsExactly(AWS_CRT_SERVICE);
    }

    @Test
    void azureTransportIsJdkClient() {
        HttpClient client = HttpClient.createDefault();
        assertThat(client.getClass().getName()).isEqualTo(AZURE_JDK_CLIENT);
    }

    private static <S> List<String> providers(Class<S> service) {
        ServiceLoader<S> loader = ServiceLoader.load(service);
        return loader.stream().map(provider -> provider.type().getName()).toList();
    }

    private static void assertMissing(String className) {
        assertThatThrownBy(() -> Class.forName(className)).isInstanceOf(ClassNotFoundException.class);
    }

    private static void assertPresent(String className) {
        assertThatCode(() -> Class.forName(className)).doesNotThrowAnyException();
    }
}
