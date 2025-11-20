package com.akto.dto.type.standard;

import java.util.regex.Pattern;

public class VinValidator {

    private static final Pattern VIN_PATTERN = Pattern.compile("^[A-HJ-NPR-Z0-9]{17}$");
    
    // Weights for each position (1–17)
    private static final int[] WEIGHTS = {
            8, 7, 6, 5, 4, 3, 2, 10, 0, 9, 8, 7, 6, 5, 4, 3, 2
    };

    /**
     * Validates a VIN: format + checksum (9th character).
     */
    public static boolean isValid(String vin) {
        if (vin == null) {
            return false;
        }

        vin = vin.trim().toUpperCase();

        if (!VIN_PATTERN.matcher(vin).matches()) {
            return false;
        }

        // 2. Compute checksum
        int sum = 0;
        for (int i = 0; i < 17; i++) {
            char c = vin.charAt(i);
            int value = transliterate(c);
            if (value == -1) {
                return false; // invalid character just in case
            }
            sum += value * WEIGHTS[i];
        }

        int checksum = sum % 11;
        char expectedCheckDigit = (checksum == 10) ? 'X' : (char) ('0' + checksum);

        // 9th character (index 8) is the check digit
        char actualCheckDigit = vin.charAt(8);

        return actualCheckDigit == expectedCheckDigit;
    }

    /**
     * Transliterates a VIN character to its numeric value for checksum.
     * Digits 0–9 → 0–9
     * Letters mapped per ISO 3779.
     */
    private static int transliterate(char c) {
        if (c >= '0' && c <= '9') {
            return c - '0';
        }

        switch (c) {
            case 'A': case 'J':             return 1;
            case 'B': case 'K': case 'S':   return 2;
            case 'C': case 'L': case 'T':   return 3;
            case 'D': case 'M': case 'U':   return 4;
            case 'E': case 'N': case 'V':   return 5;
            case 'F': case 'W':             return 6;
            case 'G': case 'P': case 'X':   return 7;
            case 'H': case 'Y':             return 8;
            case 'R': case 'Z':             return 9;
            default:
                // Should not happen if regex is correct (I, O, Q excluded),
                // but we keep this for safety.
                return -1;
        }
    }
}