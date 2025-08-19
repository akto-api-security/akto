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

}
