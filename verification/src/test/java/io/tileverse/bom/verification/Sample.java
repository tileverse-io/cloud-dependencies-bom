/*
 * Copyright (c) 2026 Multiversio LLC.
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.tileverse.bom.verification;

/** The object written and read back by the integration tests. */
final class Sample {

    static final String KEY = "verification/sample.txt";

    static final String CONTENT = "hello cloud dependencies";

    /** Offset and length of the word "cloud" in {@link #CONTENT}, read back with a range request. */
    static final int RANGE_OFFSET = 6;

    static final int RANGE_LENGTH = 5;

    static final String RANGE_CONTENT = "cloud";

    private Sample() {}
}
