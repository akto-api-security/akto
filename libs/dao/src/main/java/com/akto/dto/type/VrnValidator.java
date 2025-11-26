package com.akto.dto.type;

import java.util.regex.Pattern;

public class VrnValidator {

    /**
     * UK VRN format (2001-present): AB12 XYZ or AB12XYZ
     * - First 2 letters: Regional identifier (DVLA memory tag)
     * - 2 digits: Registration year (01-99, updated March and September)
     * - Last 3 characters: Random letters (excluding I, Q, Z in certain positions)
     *
     * Pattern: [A-Z]{2}[0-9]{2}[A-Z]{3}
     * Excludes: I, O, Q (commonly confused characters)
     */
    private static final Pattern VRN_CURRENT_PATTERN = Pattern.compile(
        "^[A-Z]{2}[0-9]{2}[A-Z]{3}$"
    );

    /**
     * Legacy UK VRN formats for backward compatibility:
     *
     * 1. Suffix format (1963-1983): ABC 123X or ABC123X
     *    - 1-3 letters, 1-3 digits, 1 letter
     *
     * 2. Prefix format (1983-2001): X123 ABC or X123ABC
     *    - 1 letter, 1-3 digits, 3 letters
     */
    private static final Pattern VRN_SUFFIX_PATTERN = Pattern.compile(
        "^[A-Z]{1,3}[0-9]{1,3}[A-Z]$"
    );

    private static final Pattern VRN_PREFIX_PATTERN = Pattern.compile(
        "^[A-Z][0-9]{1,3}[A-Z]{3}$"
    );

    /**
     * Validates a UK Vehicle Registration Number (VRN).
     * Supports current format (2001-present) and legacy formats.
     *
     * @param vrn The VRN string to validate
     * @return true if the VRN matches a valid UK format, false otherwise
     */
    public static boolean isValid(String vrn) {
        if (vrn == null || vrn.isEmpty()) {
            return false;
        }

        // Remove spaces and convert to uppercase
        vrn = vrn.trim().replaceAll("\\s+", "").toUpperCase();

        // Check minimum and maximum length
        if (vrn.length() < 4 || vrn.length() > 7) {
            return false;
        }

        // Check against current format (most common)
        if (VRN_CURRENT_PATTERN.matcher(vrn).matches()) {
            return validateCurrentFormat(vrn);
        }

        // Check against legacy suffix format
        if (VRN_SUFFIX_PATTERN.matcher(vrn).matches()) {
            return true;
        }

        // Check against legacy prefix format
        if (VRN_PREFIX_PATTERN.matcher(vrn).matches()) {
            return true;
        }

        return false;
    }

    /**
     * Validates the current UK VRN format with additional business rules.
     *
     * Rules:
     * - First letter cannot be Q (reserved for vehicles without age identifier)
     * - Second letter cannot be I (avoided to prevent confusion)
     * - Last 3 letters cannot contain I, Q (commonly confused)
     * - Year identifier (digits) must be valid (51-99 for Sept-Feb, 01-50 for March-Aug)
     *
     * @param vrn The normalized VRN (already uppercase, no spaces)
     * @return true if valid according to DVLA rules
     */
    private static boolean validateCurrentFormat(String vrn) {
        // Extract components
        char firstLetter = vrn.charAt(0);
        char secondLetter = vrn.charAt(1);
        String yearDigits = vrn.substring(2, 4);
        String randomLetters = vrn.substring(4, 7);

        // First letter should not be Q (reserved)
        if (firstLetter == 'Q') {
            return false;
        }

        // Second letter should not be I (avoided)
        if (secondLetter == 'I') {
            return false;
        }

        // Last 3 letters should not contain I or Q
        if (randomLetters.contains("I") || randomLetters.contains("Q")) {
            return false;
        }

        // Year identifier validation (optional - can be relaxed)
        // Valid ranges: 51-99 (Sept-Feb) or 01-50 (March-Aug)
        // We'll be lenient and just check it's not 00
        int year = Integer.parseInt(yearDigits);
        if (year < 1) {
            return false;
        }

        return true;
    }

    /**
     * Normalizes a VRN by removing spaces and converting to uppercase.
     * Useful for storage and comparison.
     *
     * @param vrn The VRN to normalize
     * @return Normalized VRN or null if input is null
     */
    public static String normalize(String vrn) {
        if (vrn == null) {
            return null;
        }
        return vrn.trim().replaceAll("\\s+", "").toUpperCase();
    }
}
