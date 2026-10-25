package com.learning.english.util;

import java.util.Locale;

/**
 * Standard text normalization utility shared with Frontend.
 * Follows the 4-step pipeline:
 * 1. Trim whitespace
 * 2. Lowercase (Locale.ENGLISH)
 * 3. Strip non-letter boundary characters (^[^\\p{L}]+|[^\\p{L}]+$)
 * 4. Collapse multiple spaces into a single space
 */
public final class TextNormalizationUtil {

    private TextNormalizationUtil() {
        // Prevent instantiation
    }

    public static String normalizeText(String input) {
        if (input == null) {
            return "";
        }
        String text = input.trim().toLowerCase(Locale.ENGLISH);
        // Strip non-letter characters at beginning or end
        text = text.replaceAll("^[^\\p{L}]+|[^\\p{L}]+$", "");
        // Collapse whitespace
        text = text.replaceAll("\\s+", " ").trim();
        return text;
    }
}
