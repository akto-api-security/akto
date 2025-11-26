package com.akto.dto.type;

import org.junit.Test;
import static org.junit.Assert.*;

public class TestVrnValidator {

    @Test
    public void testValidCurrentFormatVRNs() {
        // Valid current format VRNs (2001-present)
        assertTrue("AB12XYZ should be valid", VrnValidator.isValid("AB12XYZ"));
        assertTrue("AB12 XYZ with space should be valid", VrnValidator.isValid("AB12 XYZ"));
        assertTrue("ab12xyz lowercase should be valid", VrnValidator.isValid("ab12xyz"));
        assertTrue("AA99ZZZ should be valid", VrnValidator.isValid("AA99ZZZ"));
        assertTrue("BD51SMR should be valid", VrnValidator.isValid("BD51SMR"));
        assertTrue("LA51 ABC should be valid", VrnValidator.isValid("LA51 ABC"));
        assertTrue("KA02 HHH should be valid", VrnValidator.isValid("KA02 HHH"));

        // Valid with different year identifiers
        assertTrue("AB01XYZ should be valid", VrnValidator.isValid("AB01XYZ"));
        assertTrue("AB51XYZ (September registration) should be valid", VrnValidator.isValid("AB51XYZ"));
        assertTrue("AB99XYZ should be valid", VrnValidator.isValid("AB99XYZ"));
        assertTrue("AB23XYZ should be valid", VrnValidator.isValid("AB23XYZ"));
    }

    @Test
    public void testInvalidCurrentFormatVRNs() {
        // First letter Q (reserved)
        assertFalse("QB12XYZ with Q should be invalid", VrnValidator.isValid("QB12XYZ"));

        // Second letter I (avoided)
        assertFalse("AI12XYZ with I should be invalid", VrnValidator.isValid("AI12XYZ"));

        // Last three letters containing I
        assertFalse("AB12XIZ with I in last three should be invalid", VrnValidator.isValid("AB12XIZ"));
        assertFalse("AB12IXZ with I in last three should be invalid", VrnValidator.isValid("AB12IXZ"));

        // Last three letters containing Q
        assertFalse("AB12XQZ with Q in last three should be invalid", VrnValidator.isValid("AB12XQZ"));
        assertFalse("AB12QXZ with Q in last three should be invalid", VrnValidator.isValid("AB12QXZ"));

        // Year identifier 00 (invalid)
        assertFalse("AB00XYZ with year 00 should be invalid", VrnValidator.isValid("AB00XYZ"));

        // Wrong format - too many characters
        assertFalse("AB123XYZ with too many digits should be invalid", VrnValidator.isValid("AB123XYZ"));

        // Wrong format - too few characters
        assertFalse("A12XYZ with one letter should be invalid", VrnValidator.isValid("A12XYZ"));

        // Wrong format - letters in wrong place
        assertFalse("ABCDXYZ with four letters at start should be invalid", VrnValidator.isValid("ABCDXYZ"));
    }

    @Test
    public void testValidLegacySuffixFormatVRNs() {
        // Valid suffix format VRNs (1963-1983)
        assertTrue("ABC123X should be valid", VrnValidator.isValid("ABC123X"));
        assertTrue("ABC 123X with space should be valid", VrnValidator.isValid("ABC 123X"));
        assertTrue("A1X should be valid", VrnValidator.isValid("A1X"));
        assertTrue("ABC1Y should be valid", VrnValidator.isValid("ABC1Y"));
        assertTrue("AB12Z should be valid", VrnValidator.isValid("AB12Z"));
        assertTrue("XYZ999P should be valid", VrnValidator.isValid("XYZ999P"));
    }

    @Test
    public void testValidLegacyPrefixFormatVRNs() {
        // Valid prefix format VRNs (1983-2001)
        assertTrue("X123ABC should be valid", VrnValidator.isValid("X123ABC"));
        assertTrue("X123 ABC with space should be valid", VrnValidator.isValid("X123 ABC"));
        assertTrue("A1XYZ should be valid", VrnValidator.isValid("A1XYZ"));
        assertTrue("Y999ABC should be valid", VrnValidator.isValid("Y999ABC"));
        assertTrue("P123XYZ should be valid", VrnValidator.isValid("P123XYZ"));
    }

    @Test
    public void testInvalidVRNs() {
        // Null and empty
        assertFalse("Null should be invalid", VrnValidator.isValid(null));
        assertFalse("Empty string should be invalid", VrnValidator.isValid(""));
        assertFalse("Whitespace only should be invalid", VrnValidator.isValid("   "));

        // Too short
        assertFalse("ABC (3 chars) should be invalid", VrnValidator.isValid("ABC"));
        assertFalse("A1B (3 chars) should be invalid", VrnValidator.isValid("A1B"));

        // Too long
        assertFalse("AB12XYZAA (9 chars) should be invalid", VrnValidator.isValid("AB12XYZAA"));
        assertFalse("ABC1234X (8 chars but wrong format) should be invalid", VrnValidator.isValid("ABC1234X"));

        // Special characters
        assertFalse("AB-12-XYZ with dashes should be invalid", VrnValidator.isValid("AB-12-XYZ"));
        assertFalse("AB12@XYZ with special char should be invalid", VrnValidator.isValid("AB12@XYZ"));
        assertFalse("AB12_XYZ with underscore should be invalid", VrnValidator.isValid("AB12_XYZ"));

        // Numbers in wrong places
        assertFalse("1B12XYZ with number at start should be invalid", VrnValidator.isValid("1B12XYZ"));
        assertFalse("A112XYZ with number in second position should be invalid", VrnValidator.isValid("A112XYZ"));

        // Random invalid formats
        assertFalse("12345AB should be invalid", VrnValidator.isValid("12345AB"));
        assertFalse("ABCDEFG should be invalid", VrnValidator.isValid("ABCDEFG"));
        assertFalse("1234567 should be invalid", VrnValidator.isValid("1234567"));
    }

    @Test
    public void testWhitespaceHandling() {
        // Leading/trailing spaces
        assertTrue("  AB12XYZ   with surrounding spaces should be valid", VrnValidator.isValid("  AB12XYZ   "));
        assertTrue("  AB12 XYZ   with surrounding spaces should be valid", VrnValidator.isValid("  AB12 XYZ   "));

        // Multiple spaces in middle
        assertTrue("AB12  XYZ with double space should be valid", VrnValidator.isValid("AB12  XYZ"));
        assertTrue("AB  12  XYZ with multiple spaces should be valid", VrnValidator.isValid("AB  12  XYZ"));
    }

    @Test
    public void testCaseInsensitivity() {
        // Mixed case
        assertTrue("Ab12XyZ mixed case should be valid", VrnValidator.isValid("Ab12XyZ"));
        assertTrue("aB12xYz mixed case should be valid", VrnValidator.isValid("aB12xYz"));

        // Lowercase
        assertTrue("ab12xyz lowercase should be valid", VrnValidator.isValid("ab12xyz"));

        // Uppercase
        assertTrue("AB12XYZ uppercase should be valid", VrnValidator.isValid("AB12XYZ"));
    }

    @Test
    public void testNormalizeMethod() {
        // Test normalization
        assertEquals("AB12XYZ", VrnValidator.normalize("ab12xyz"));
        assertEquals("AB12XYZ", VrnValidator.normalize("AB12 XYZ"));
        assertEquals("AB12XYZ", VrnValidator.normalize("  ab12 xyz  "));
        assertEquals("AB12XYZ", VrnValidator.normalize("AB  12  XYZ"));

        // Null handling
        assertNull("Null should return null", VrnValidator.normalize(null));
    }

    @Test
    public void testRealWorldExamples() {
        // Real-world UK VRN examples (anonymized but realistic)
        assertTrue("BD51SMR should be valid", VrnValidator.isValid("BD51SMR"));
        assertTrue("LA51ABC should be valid", VrnValidator.isValid("LA51ABC"));
        assertTrue("AB12CDE should be valid", VrnValidator.isValid("AB12CDE"));
        assertTrue("KA02HHH should be valid", VrnValidator.isValid("KA02HHH"));
        assertTrue("AB67CDF should be valid", VrnValidator.isValid("AB67CDF"));
        assertTrue("PO57XYZ should be valid", VrnValidator.isValid("PO57XYZ"));

        // Legacy examples
        assertTrue("ABC123X should be valid", VrnValidator.isValid("ABC123X"));
        assertTrue("A1ABC should be valid", VrnValidator.isValid("A1ABC"));
    }

    @Test
    public void testEdgeCases() {
        // Minimum valid length (legacy)
        assertTrue("A1X (4 chars) should be valid", VrnValidator.isValid("A1X"));

        // Maximum valid length
        assertTrue("ABC123X (7 chars) should be valid", VrnValidator.isValid("ABC123X"));
        assertTrue("AB12XYZ (7 chars) should be valid", VrnValidator.isValid("AB12XYZ"));

        // Just below minimum
        assertFalse("ABC (3 chars) should be invalid", VrnValidator.isValid("ABC"));

        // Just above maximum
        assertFalse("ABC1234X (8 chars) should be invalid", VrnValidator.isValid("ABC1234X"));
    }
}
