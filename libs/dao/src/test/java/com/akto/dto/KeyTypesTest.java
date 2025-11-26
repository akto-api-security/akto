package com.akto.dto;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;

import org.junit.Before;
import org.junit.Test;

import com.akto.dto.type.AccountDataTypesInfo;
import com.akto.dto.type.KeyTypes;
import com.akto.dto.type.SingleTypeInfo;
import com.akto.util.filter.DictionaryFilter;

public class KeyTypesTest {
    
    @Before
    public void initMain() {
        DictionaryFilter.readDictionaryBinary();

        Map<String, AktoDataType> aktoDataTypeMap = new HashMap<>();
        aktoDataTypeMap.put("JWT", new AktoDataType(null, false, null, 0, new IgnoreData(new HashMap<>(), new HashSet<>()), false, true));
        aktoDataTypeMap.put("PHONE_NUMBER", new AktoDataType(null, false, null, 0, new IgnoreData(new HashMap<>(), new HashSet<>()), false, true));
        aktoDataTypeMap.put("CREDIT_CARD", new AktoDataType(null, false, null, 0, new IgnoreData(new HashMap<>(), new HashSet<>()), false, true));
        aktoDataTypeMap.put("IP_ADDRESS", new AktoDataType(null, false, null, 0, new IgnoreData(new HashMap<>(), new HashSet<>()), false, true));
        aktoDataTypeMap.put("EMAIL", new AktoDataType(null, false, null, 0, new IgnoreData(new HashMap<>(), new HashSet<>()), false, true));
        aktoDataTypeMap.put("SSN", new AktoDataType(null, false, null, 0, new IgnoreData(new HashMap<>(), new HashSet<>()), false, true));
        aktoDataTypeMap.put("UUID", new AktoDataType(null, false, null, 0, new IgnoreData(new HashMap<>(), new HashSet<>()), false, true));
        aktoDataTypeMap.put("URL", new AktoDataType(null, false, null, 0, new IgnoreData(new HashMap<>(), new HashSet<>()), false, true));
        aktoDataTypeMap.put("VIN", new AktoDataType(null, false, null, 0, new IgnoreData(new HashMap<>(), new HashSet<>()), false, true));
        aktoDataTypeMap.put("VRN", new AktoDataType(null, false, null, 0, new IgnoreData(new HashMap<>(), new HashSet<>()), false, true));

        AccountDataTypesInfo info = SingleTypeInfo.getAccountToDataTypesInfo().get(12389);
        if (info == null) {
            info = new AccountDataTypesInfo();
        }
        info.setAktoDataTypeMap(aktoDataTypeMap);
        SingleTypeInfo.getAccountToDataTypesInfo().put(12389, info);
    }

    private static boolean ok(String s) {
        return KeyTypes.isPhoneNumber(s);
    }

    @Test
    public void rejectsUppercaseLetters() {
        String[] samples = {
                "A12345678",
                "1234B5678",
                "+91C234567890",
                "US2025550123A"
        };
        for (String s : samples) {
            assertFalse("Should reject: " + s, ok(s));
        }
    }

    @Test
    public void rejectsLowercaseLetters() {
        String[] samples = {
                "a12345678",
                "1234b5678",
                "+91c234567890",
                "us2025550123a"
        };
        for (String s : samples) {
            assertFalse("Should reject: " + s, ok(s));
        }
    }

    @Test
    public void rejectsTooShortOrTooLong() {
        assertFalse(ok("1234567"));                 // 7 chars
        assertFalse(ok("12345678901234567"));       // 17 chars
    }

    @Test
    public void rejectsNull() {
        assertFalse(ok(null));
    }

    @Test
    public void acceptsValidWithPlus() {
        assertTrue(ok("+12022550123"));             // US example number
        assertTrue(ok("+1 202-555-0123"));          // formatted US
    }

    @Test
    public void rejectsInvalidButNumeric() {
        assertFalse(ok("+99912345678"));            // invalid country code
        assertFalse(ok("+1202555"));                // implausible
        assertFalse(ok("+1 202 555 01234 9999"));   // too long after normalization
    }

    @Test
    public void acceptsCommonFormattingChars() {
        //assertTrue(ok("(202) 555-0123"));           // lib should normalize and validate when region can be inferred
        assertTrue(ok("+44 20 7946 0958"));         // UK style with spaces
    }

    // ========== UK VRN (Vehicle Registration Number) Tests ==========

    @Test
    public void testVRN_CurrentFormat_Valid() {
        // Current format (2001-present): AB12 XYZ
        assertTrue("Should detect AB12 XYZ as VRN",
            KeyTypes.findSubType("AB12 XYZ", "vehicleReg", null) == SingleTypeInfo.VRN);
        assertTrue("Should detect AB12XYZ as VRN",
            KeyTypes.findSubType("AB12XYZ", "vrn", null) == SingleTypeInfo.VRN);
        assertTrue("Should detect BD51 SMR as VRN",
            KeyTypes.findSubType("BD51 SMR", "registrationNumber", null) == SingleTypeInfo.VRN);
        assertTrue("Should detect LA56ABC as VRN",
            KeyTypes.findSubType("LA56ABC", "plate", null) == SingleTypeInfo.VRN);
    }

    @Test
    public void testVRN_LegacyFormats_Valid() {
        // Prefix format (1983-2001): A123 BCD
        assertTrue("Should detect A123 BCD as VRN",
            KeyTypes.findSubType("A123 BCD", "vrn", null) == SingleTypeInfo.VRN);
        assertTrue("Should detect P789XYZ as VRN",
            KeyTypes.findSubType("P789XYZ", "registration", null) == SingleTypeInfo.VRN);

        // Suffix format (1963-1983): ABC 123D
        assertTrue("Should detect ABC 123D as VRN",
            KeyTypes.findSubType("ABC 123D", "vrn", null) == SingleTypeInfo.VRN);
        assertTrue("Should detect XYZ789K as VRN",
            KeyTypes.findSubType("XYZ789K", "plate", null) == SingleTypeInfo.VRN);

        // Dateless format (pre-1963): ABC 123
        assertTrue("Should detect ABC 123 as VRN",
            KeyTypes.findSubType("ABC 123", "vrn", null) == SingleTypeInfo.VRN);
        assertTrue("Should detect DEF456 as VRN",
            KeyTypes.findSubType("DEF456", "registration", null) == SingleTypeInfo.VRN);
    }

    @Test
    public void testVRN_Invalid() {
        // Should reject prohibited letters (I, Q, O)
        assertFalse("Should reject VRN with I",
            KeyTypes.findSubType("IB12 XYZ", "vrn", null) == SingleTypeInfo.VRN);
        assertFalse("Should reject VRN with Q",
            KeyTypes.findSubType("QB12 XYZ", "vrn", null) == SingleTypeInfo.VRN);
        assertFalse("Should reject VRN with O",
            KeyTypes.findSubType("OB12 XYZ", "vrn", null) == SingleTypeInfo.VRN);

        // Should reject invalid formats
        assertFalse("Should reject too short",
            KeyTypes.findSubType("AB1", "vrn", null) == SingleTypeInfo.VRN);
        assertFalse("Should reject too long",
            KeyTypes.findSubType("AB123XYZW", "vrn", null) == SingleTypeInfo.VRN);
        assertFalse("Should reject invalid age 00",
            KeyTypes.findSubType("AB00 XYZ", "vrn", null) == SingleTypeInfo.VRN);
    }

    @Test
    public void testVRN_CaseInsensitive() {
        // Should handle various cases
        assertTrue("Should detect lowercase vrn",
            KeyTypes.findSubType("ab12xyz", "vrn", null) == SingleTypeInfo.VRN);
        assertTrue("Should detect mixed case vrn",
            KeyTypes.findSubType("Ab12XyZ", "vrn", null) == SingleTypeInfo.VRN);
    }

}
