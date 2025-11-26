package com.akto.dto.type.standard;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * Unit tests for UK Vehicle Registration Number (VRN) validation.
 * Tests cover current format (2001-present) and legacy formats.
 */
public class VrnValidatorTest {

    // ========== Current Format Tests (2001-present): AB12 XYZ ==========

    @Test
    public void testCurrentFormat_Valid() {
        // Valid current format examples
        assertTrue("Should accept AB12 XYZ", VrnValidator.isValid("AB12 XYZ"));
        assertTrue("Should accept AB12XYZ", VrnValidator.isValid("AB12XYZ"));
        assertTrue("Should accept lowercase", VrnValidator.isValid("ab12xyz"));
        assertTrue("Should accept mixed case", VrnValidator.isValid("Ab12XyZ"));

        // Real-world examples
        assertTrue("Should accept BD51 SMR", VrnValidator.isValid("BD51 SMR"));
        assertTrue("Should accept LA56 ABC", VrnValidator.isValid("LA56 ABC"));
        assertTrue("Should accept KP02 LMN", VrnValidator.isValid("KP02LMN"));

        // September onwards registrations (51-69)
        assertTrue("Should accept AB51 XYZ", VrnValidator.isValid("AB51 XYZ"));
        assertTrue("Should accept CD69 PRS", VrnValidator.isValid("CD69 PRS"));

        // With extra whitespace (should normalize)
        assertTrue("Should normalize whitespace", VrnValidator.isValid("  AB12 XYZ  "));
        assertTrue("Should normalize multiple spaces", VrnValidator.isValid("AB12  XYZ"));
    }

    @Test
    public void testCurrentFormat_Invalid() {
        // Too few/many characters
        assertFalse("Should reject AB1 XYZ", VrnValidator.isValid("AB1 XYZ"));
        assertFalse("Should reject AB123 XYZ", VrnValidator.isValid("AB123 XYZ"));
        assertFalse("Should reject A12 XYZ", VrnValidator.isValid("A12 XYZ"));
        assertFalse("Should reject ABC12 XYZ", VrnValidator.isValid("ABC12 XYZ"));
        assertFalse("Should reject AB12 XY", VrnValidator.isValid("AB12 XY"));
        assertFalse("Should reject AB12 XYZW", VrnValidator.isValid("AB12 XYZW"));

        // Invalid age identifier (00 is not valid)
        assertFalse("Should reject AB00 XYZ", VrnValidator.isValid("AB00 XYZ"));

        // Numbers in wrong positions
        assertFalse("Should reject A212 XYZ", VrnValidator.isValid("A212 XYZ"));
        assertFalse("Should reject AB12 X2Z", VrnValidator.isValid("AB12 X2Z"));
    }

    @Test
    public void testCurrentFormat_Specific() {
        // Test isCurrentFormat specifically
        assertTrue("Should match current format", VrnValidator.isCurrentFormat("AB12 XYZ"));
        assertFalse("Should not match prefix format", VrnValidator.isCurrentFormat("A123 BCD"));
        assertFalse("Should not match suffix format", VrnValidator.isCurrentFormat("ABC 123D"));
        assertFalse("Should reject age 00", VrnValidator.isCurrentFormat("AB00 XYZ"));
    }

    // ========== Prefix Format Tests (1983-2001): A123 BCD ==========

    @Test
    public void testPrefixFormat_Valid() {
        assertTrue("Should accept A123 BCD", VrnValidator.isValid("A123 BCD"));
        assertTrue("Should accept A1 BCD", VrnValidator.isValid("A1 BCD"));
        assertTrue("Should accept A12 BCD", VrnValidator.isValid("A12 BCD"));
        assertTrue("Should accept A123BCD", VrnValidator.isValid("A123BCD"));
        assertTrue("Should accept lowercase", VrnValidator.isValid("a123bcd"));

        // Real-world examples
        assertTrue("Should accept P789 GHJ", VrnValidator.isValid("P789 GHJ"));
        assertTrue("Should accept R456 XYZ", VrnValidator.isValid("R456 XYZ"));
    }

    @Test
    public void testPrefixFormat_Invalid() {
        // Too many letters at start
        assertFalse("Should reject AB123 BCD", VrnValidator.isValid("AB123 BCD"));

        // Too few/many letters at end
        assertFalse("Should reject A123 BC", VrnValidator.isValid("A123 BC"));
        assertFalse("Should reject A123 BCDE", VrnValidator.isValid("A123 BCDE"));

        // Too many digits
        assertFalse("Should reject A1234 BCD", VrnValidator.isValid("A1234 BCD"));
    }

    // ========== Suffix Format Tests (1963-1983): ABC 123D ==========

    @Test
    public void testSuffixFormat_Valid() {
        assertTrue("Should accept ABC 123D", VrnValidator.isValid("ABC 123D"));
        assertTrue("Should accept ABC 1D", VrnValidator.isValid("ABC 1D"));
        assertTrue("Should accept ABC 12D", VrnValidator.isValid("ABC 12D"));
        assertTrue("Should accept ABC123D", VrnValidator.isValid("ABC123D"));
        assertTrue("Should accept lowercase", VrnValidator.isValid("abc123d"));

        // Real-world examples
        assertTrue("Should accept XYZ 789K", VrnValidator.isValid("XYZ 789K"));
        assertTrue("Should accept RST 456W", VrnValidator.isValid("RST 456W"));
    }

    @Test
    public void testSuffixFormat_Invalid() {
        // Too few/many letters at start
        assertFalse("Should reject AB 123D", VrnValidator.isValid("AB 123D"));
        assertFalse("Should reject ABCD 123D", VrnValidator.isValid("ABCD 123D"));

        // Too many letters at end
        assertFalse("Should reject ABC 123DE", VrnValidator.isValid("ABC 123DE"));

        // Too many digits
        assertFalse("Should reject ABC 1234D", VrnValidator.isValid("ABC 1234D"));
    }

    // ========== Dateless Format Tests (pre-1963): ABC 123 ==========

    @Test
    public void testDatelessFormat_Valid() {
        assertTrue("Should accept ABC 123", VrnValidator.isValid("ABC 123"));
        assertTrue("Should accept ABC 1", VrnValidator.isValid("ABC 1"));
        assertTrue("Should accept ABC 12", VrnValidator.isValid("ABC 12"));
        assertTrue("Should accept ABC123", VrnValidator.isValid("ABC123"));
        assertTrue("Should accept lowercase", VrnValidator.isValid("abc123"));

        // Real-world examples
        assertTrue("Should accept PRS 789", VrnValidator.isValid("PRS 789"));
        assertTrue("Should accept DEF 456", VrnValidator.isValid("DEF 456"));
    }

    @Test
    public void testDatelessFormat_Invalid() {
        // Too few/many letters
        assertFalse("Should reject AB 123", VrnValidator.isValid("AB 123"));
        assertFalse("Should reject ABCD 123", VrnValidator.isValid("ABCD 123"));

        // Too many digits
        assertFalse("Should reject ABC 1234", VrnValidator.isValid("ABC 1234"));
    }

    // ========== Prohibited Letters Tests ==========

    @Test
    public void testProhibitedLetters_I() {
        // Letter I is prohibited (confusion with 1)
        assertFalse("Should reject I in position 1", VrnValidator.isValid("IB12 XYZ"));
        assertFalse("Should reject I in position 2", VrnValidator.isValid("AI12 XYZ"));
        assertFalse("Should reject I in position 5", VrnValidator.isValid("AB12 IYZ"));
        assertFalse("Should reject I in position 6", VrnValidator.isValid("AB12 XIZ"));
        assertFalse("Should reject I in position 7", VrnValidator.isValid("AB12 XYI"));
        assertFalse("Should reject I in prefix", VrnValidator.isValid("I123 BCD"));
        assertFalse("Should reject I in suffix", VrnValidator.isValid("ABC 123I"));
    }

    @Test
    public void testProhibitedLetters_Q() {
        // Letter Q is prohibited
        assertFalse("Should reject Q in current format", VrnValidator.isValid("QB12 XYZ"));
        assertFalse("Should reject Q in position 2", VrnValidator.isValid("AQ12 XYZ"));
        assertFalse("Should reject Q in last section", VrnValidator.isValid("AB12 QYZ"));
        assertFalse("Should reject Q in prefix", VrnValidator.isValid("Q123 BCD"));
        assertFalse("Should reject Q in suffix", VrnValidator.isValid("ABC 123Q"));
    }

    @Test
    public void testProhibitedLetters_O() {
        // Letter O is prohibited (confusion with 0)
        assertFalse("Should reject O in current format", VrnValidator.isValid("OB12 XYZ"));
        assertFalse("Should reject O in position 2", VrnValidator.isValid("AO12 XYZ"));
        assertFalse("Should reject O in last section", VrnValidator.isValid("AB12 OYZ"));
        assertFalse("Should reject O in prefix", VrnValidator.isValid("O123 BCD"));
        assertFalse("Should reject O in suffix", VrnValidator.isValid("ABC 123O"));
    }

    // ========== Edge Cases and Invalid Input ==========

    @Test
    public void testEdgeCases_Null() {
        assertFalse("Should reject null", VrnValidator.isValid(null));
        assertFalse("Should reject null in isCurrentFormat", VrnValidator.isCurrentFormat(null));
    }

    @Test
    public void testEdgeCases_Empty() {
        assertFalse("Should reject empty string", VrnValidator.isValid(""));
        assertFalse("Should reject whitespace only", VrnValidator.isValid("   "));
        assertFalse("Should reject empty in isCurrentFormat", VrnValidator.isCurrentFormat(""));
    }

    @Test
    public void testEdgeCases_TooShort() {
        assertFalse("Should reject 3 chars", VrnValidator.isValid("ABC"));
        assertFalse("Should reject 2 chars", VrnValidator.isValid("AB"));
        assertFalse("Should reject 1 char", VrnValidator.isValid("A"));
    }

    @Test
    public void testEdgeCases_TooLong() {
        assertFalse("Should reject 8+ chars", VrnValidator.isValid("ABC12345"));
        assertFalse("Should reject very long", VrnValidator.isValid("ABCD1234EFGH"));
    }

    @Test
    public void testInvalidChars_Numbers() {
        assertFalse("Should reject numbers only", VrnValidator.isValid("1234567"));
        assertFalse("Should reject too many numbers", VrnValidator.isValid("AB1234 XYZ"));
    }

    @Test
    public void testInvalidChars_SpecialChars() {
        assertFalse("Should reject hyphen", VrnValidator.isValid("AB-12 XYZ"));
        assertFalse("Should reject underscore", VrnValidator.isValid("AB_12 XYZ"));
        assertFalse("Should reject symbols", VrnValidator.isValid("AB12@XYZ"));
        assertFalse("Should reject parentheses", VrnValidator.isValid("(AB12) XYZ"));
    }

    // ========== Real-World Valid Examples ==========

    @Test
    public void testRealWorld_CurrentFormat() {
        // Common current format plates
        String[] validPlates = {
            "AB51 SMR",  // 2001 September
            "BD02 XYZ",  // 2002 March
            "LA56 ABC",  // 2006 September
            "KP12 LMN",  // 2012 March
            "BD21 PRS",  // 2021 March
            "AB70 DEF",  // 2020 September
            "CD15 GHJ",  // 2015 March
            "EF68 KLM"   // 2018 September
        };

        for (String plate : validPlates) {
            assertTrue("Should accept valid plate: " + plate, VrnValidator.isValid(plate));
        }
    }

    @Test
    public void testRealWorld_LegacyFormats() {
        // Legacy format plates
        String[] validPlates = {
            "A123 BCD",  // Prefix
            "P789 XYZ",  // Prefix
            "R456 GHJ",  // Prefix
            "ABC 123D",  // Suffix
            "XYZ 789K",  // Suffix
            "RST 456W",  // Suffix
            "ABC 123",   // Dateless
            "PRS 789",   // Dateless
            "DEF 456"    // Dateless
        };

        for (String plate : validPlates) {
            assertTrue("Should accept valid legacy plate: " + plate, VrnValidator.isValid(plate));
        }
    }

    // ========== Mixed Format Tests ==========

    @Test
    public void testWhitespace_Handling() {
        // Should handle various whitespace scenarios
        assertTrue("Should handle no space", VrnValidator.isValid("AB12XYZ"));
        assertTrue("Should handle single space", VrnValidator.isValid("AB12 XYZ"));
        assertTrue("Should handle leading space", VrnValidator.isValid(" AB12 XYZ"));
        assertTrue("Should handle trailing space", VrnValidator.isValid("AB12 XYZ "));
        assertTrue("Should handle both", VrnValidator.isValid("  AB12 XYZ  "));
        assertTrue("Should normalize double space", VrnValidator.isValid("AB12  XYZ"));
    }

    @Test
    public void testCase_Insensitivity() {
        // Should handle all case variations
        assertTrue("Should accept uppercase", VrnValidator.isValid("AB12XYZ"));
        assertTrue("Should accept lowercase", VrnValidator.isValid("ab12xyz"));
        assertTrue("Should accept mixed", VrnValidator.isValid("Ab12XyZ"));
        assertTrue("Should accept mixed 2", VrnValidator.isValid("aB12xYz"));
    }
}
