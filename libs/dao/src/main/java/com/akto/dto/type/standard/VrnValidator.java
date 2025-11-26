package com.akto.dto.type.standard;

import java.util.regex.Pattern;

/**
 * Validator for UK Vehicle Registration Numbers (VRN).
 * Supports current format (2001-present) and legacy formats.
 */
public class VrnValidator {

    // Current format (2001-present): AB12 XYZ
    // - 2 letters (area code)
    // - 2 digits (age identifier: 01-99, where 51-99 indicates September-February)
    // - 3 letters (random)
    // Optional space between number and letters
    private static final Pattern CURRENT_FORMAT = Pattern.compile(
        "^[A-Z]{2}[0-9]{2}\\s?[A-Z]{3}$"
    );

    // Prefix format (1983-2001): A123 BCD
    // - 1 letter (year identifier)
    // - 1-3 digits
    // - 3 letters (area code)
    private static final Pattern PREFIX_FORMAT = Pattern.compile(
        "^[A-Z][0-9]{1,3}\\s?[A-Z]{3}$"
    );

    // Suffix format (1963-1983): ABC 123D
    // - 3 letters (area code)
    // - 1-3 digits
    // - 1 letter (year identifier)
    private static final Pattern SUFFIX_FORMAT = Pattern.compile(
        "^[A-Z]{3}\\s?[0-9]{1,3}[A-Z]$"
    );

    // Dateless format (pre-1963): ABC 123
    // - 3 letters
    // - 1-3 digits
    private static final Pattern DATELESS_FORMAT = Pattern.compile(
        "^[A-Z]{3}\\s?[0-9]{1,3}$"
    );

    // Letters not allowed in UK number plates
    private static final String PROHIBITED_LETTERS = "IQO";

    /**
     * Validates a UK Vehicle Registration Number.
     * Supports current and legacy formats.
     *
     * @param vrn the VRN string to validate
     * @return true if valid, false otherwise
     */
    public static boolean isValid(String vrn) {
        if (vrn == null || vrn.isEmpty()) {
            return false;
        }

        // Normalize: trim, uppercase, normalize whitespace
        vrn = vrn.trim().toUpperCase().replaceAll("\\s+", " ");

        // Check length (minimum 4, maximum 8 including optional space)
        int lengthWithoutSpace = vrn.replace(" ", "").length();
        if (lengthWithoutSpace < 4 || lengthWithoutSpace > 7) {
            return false;
        }

        // Check for prohibited letters (I, Q, O to avoid confusion with 1, 0)
        if (containsProhibitedLetters(vrn)) {
            return false;
        }

        // Try to match against supported formats
        return CURRENT_FORMAT.matcher(vrn).matches() ||
               PREFIX_FORMAT.matcher(vrn).matches() ||
               SUFFIX_FORMAT.matcher(vrn).matches() ||
               DATELESS_FORMAT.matcher(vrn).matches();
    }

    /**
     * Checks if the VRN contains prohibited letters (I, Q, O).
     *
     * @param vrn the VRN string (already uppercase)
     * @return true if contains prohibited letters
     */
    private static boolean containsProhibitedLetters(String vrn) {
        for (char c : PROHIBITED_LETTERS.toCharArray()) {
            if (vrn.indexOf(c) >= 0) {
                return true;
            }
        }
        return false;
    }

    /**
     * Validates specifically the current UK VRN format (2001-present).
     *
     * @param vrn the VRN string to validate
     * @return true if matches current format, false otherwise
     */
    public static boolean isCurrentFormat(String vrn) {
        if (vrn == null || vrn.isEmpty()) {
            return false;
        }

        vrn = vrn.trim().toUpperCase().replaceAll("\\s+", " ");

        if (containsProhibitedLetters(vrn)) {
            return false;
        }

        if (!CURRENT_FORMAT.matcher(vrn).matches()) {
            return false;
        }

        // Extract and validate age identifier (must be 01-99)
        String vrnNoSpace = vrn.replace(" ", "");
        String ageIdentifier = vrnNoSpace.substring(2, 4);
        int age = Integer.parseInt(ageIdentifier);

        // Valid age identifiers: 01-99 (but typically 01-69 or 51-99)
        return age >= 1 && age <= 99;
    }
}
