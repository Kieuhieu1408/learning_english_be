package com.learning.english.util;

import com.fasterxml.uuid.Generators;
import com.fasterxml.uuid.NoArgGenerator;

import java.util.UUID;

/**
 * Utility to generate RFC 9562 compliant UUIDv7 identifiers.
 * Combines 48-bit Unix timestamp millisecond precision with monotonic random sequence.
 */
public final class UuidV7Generator {

    private static final NoArgGenerator GENERATOR = Generators.timeBasedEpochGenerator();

    private UuidV7Generator() {
        // Prevent instantiation
    }

    /**
     * Generate a new UUIDv7.
     *
     * @return time-ordered UUIDv7
     */
    public static UUID generate() {
        return GENERATOR.generate();
    }
}
