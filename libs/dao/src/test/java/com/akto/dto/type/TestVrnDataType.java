package com.akto.dto.type;

import com.akto.dao.context.Context;
import com.akto.dto.AktoDataType;
import com.akto.dto.IgnoreData;
import com.akto.dto.SensitiveParamInfo;
import org.junit.Before;
import org.junit.Test;

import java.util.*;

import static org.junit.Assert.*;

public class TestVrnDataType {

    private final int ACCOUNT_ID = 1_000_000;

    @Before
    public void setUp() {
        Context.accountId.set(ACCOUNT_ID);
        Map<String, AktoDataType> aktoDataTypeMap = new HashMap<>();
        aktoDataTypeMap.put("VRN", new AktoDataType(null, false, null, 0, new IgnoreData(new HashMap<>(), new HashSet<>()), false, true));
        aktoDataTypeMap.put("VIN", new AktoDataType(null, false, null, 0, new IgnoreData(new HashMap<>(), new HashSet<>()), false, true));

        AccountDataTypesInfo info = SingleTypeInfo.getAccountToDataTypesInfo().get(ACCOUNT_ID);
        if (info == null) {
            info = new AccountDataTypesInfo();
        }
        info.setAktoDataTypeMap(aktoDataTypeMap);
        SingleTypeInfo.getAccountToDataTypesInfo().put(ACCOUNT_ID, info);
    }

    @Test
    public void testVrnDetectionCurrentFormat() {
        String url = "api/vehicles";
        String method = "GET";
        int responseCode = 200;

        Map<SensitiveParamInfo, Boolean> sensitiveParamInfoBooleanMap = new HashMap<>();

        KeyTypes keyTypes = new KeyTypes(new HashMap<>(), false);

        // Test current format VRN detection
        keyTypes.process(url, method, responseCode, false, "registration_number", "AB12XYZ",
                "u1", 0, "rawMessage", sensitiveParamInfoBooleanMap, false, Context.now());

        assertTrue("VRN should be detected", keyTypes.occurrences.containsKey(SingleTypeInfo.VRN));

        SingleTypeInfo vrnTypeInfo = keyTypes.occurrences.get(SingleTypeInfo.VRN);
        assertNotNull("VRN type info should not be null", vrnTypeInfo);
        assertNotNull("VRN values should not be null", vrnTypeInfo.getValues());
        assertTrue("VRN values should contain AB12XYZ", vrnTypeInfo.getValues().getElements().contains("AB12XYZ"));
    }

    @Test
    public void testVrnDetectionWithSpace() {
        String url = "api/vehicles";
        String method = "GET";
        int responseCode = 200;

        Map<SensitiveParamInfo, Boolean> sensitiveParamInfoBooleanMap = new HashMap<>();

        KeyTypes keyTypes = new KeyTypes(new HashMap<>(), false);

        // Test VRN with space
        keyTypes.process(url, method, responseCode, false, "vrn", "AB12 XYZ",
                "u1", 0, "rawMessage", sensitiveParamInfoBooleanMap, false, Context.now());

        assertTrue("VRN with space should be detected", keyTypes.occurrences.containsKey(SingleTypeInfo.VRN));
    }

    @Test
    public void testVrnDetectionLegacyFormats() {
        String url = "api/vehicles";
        String method = "GET";
        int responseCode = 200;

        Map<SensitiveParamInfo, Boolean> sensitiveParamInfoBooleanMap = new HashMap<>();

        KeyTypes keyTypes = new KeyTypes(new HashMap<>(), false);

        // Test suffix format
        keyTypes.process(url, method, responseCode, false, "reg", "ABC123X",
                "u1", 0, "rawMessage", sensitiveParamInfoBooleanMap, false, Context.now());

        assertTrue("Legacy suffix VRN should be detected", keyTypes.occurrences.containsKey(SingleTypeInfo.VRN));

        // Test prefix format
        KeyTypes keyTypes2 = new KeyTypes(new HashMap<>(), false);
        keyTypes2.process(url, method, responseCode, false, "plate", "X123ABC",
                "u2", 0, "rawMessage", sensitiveParamInfoBooleanMap, false, Context.now());

        assertTrue("Legacy prefix VRN should be detected", keyTypes2.occurrences.containsKey(SingleTypeInfo.VRN));
    }

    @Test
    public void testVrnNotDetectedForInvalidFormats() {
        String url = "api/vehicles";
        String method = "GET";
        int responseCode = 200;

        Map<SensitiveParamInfo, Boolean> sensitiveParamInfoBooleanMap = new HashMap<>();

        KeyTypes keyTypes = new KeyTypes(new HashMap<>(), false);

        // Test invalid VRN - should not be detected as VRN
        keyTypes.process(url, method, responseCode, false, "registration", "INVALID123",
                "u1", 0, "rawMessage", sensitiveParamInfoBooleanMap, false, Context.now());

        assertFalse("Invalid VRN should not be detected as VRN",
                keyTypes.occurrences.containsKey(SingleTypeInfo.VRN));
    }

    @Test
    public void testVrnVsVinDistinction() {
        String url = "api/vehicles";
        String method = "GET";
        int responseCode = 200;

        Map<SensitiveParamInfo, Boolean> sensitiveParamInfoBooleanMap = new HashMap<>();

        // Test VRN detection
        KeyTypes keyTypesVrn = new KeyTypes(new HashMap<>(), false);
        keyTypesVrn.process(url, method, responseCode, false, "uk_registration", "BD51SMR",
                "u1", 0, "rawMessage", sensitiveParamInfoBooleanMap, false, Context.now());

        assertTrue("UK VRN should be detected as VRN", keyTypesVrn.occurrences.containsKey(SingleTypeInfo.VRN));
        assertFalse("UK VRN should not be detected as VIN", keyTypesVrn.occurrences.containsKey(SingleTypeInfo.VIN));

        // Test VIN detection (17 character VIN should not be detected as VRN)
        KeyTypes keyTypesVin = new KeyTypes(new HashMap<>(), false);
        keyTypesVin.process(url, method, responseCode, false, "vin_number", "1HGBH41JXMN109186",
                "u2", 0, "rawMessage", sensitiveParamInfoBooleanMap, false, Context.now());

        assertFalse("VIN should not be detected as VRN", keyTypesVin.occurrences.containsKey(SingleTypeInfo.VRN));
    }

    @Test
    public void testVrnDetectionWithDifferentKeys() {
        String url = "api/vehicles";
        String method = "GET";
        int responseCode = 200;

        Map<SensitiveParamInfo, Boolean> sensitiveParamInfoBooleanMap = new HashMap<>();

        String[] validKeys = {
                "vrn", "registration", "reg", "registration_number",
                "vehicle_registration", "plate", "number_plate", "license_plate"
        };

        for (String key : validKeys) {
            KeyTypes keyTypes = new KeyTypes(new HashMap<>(), false);
            keyTypes.process(url, method, responseCode, false, key, "AB12XYZ",
                    "u1", 0, "rawMessage", sensitiveParamInfoBooleanMap, false, Context.now());

            assertTrue("VRN should be detected with key: " + key,
                    keyTypes.occurrences.containsKey(SingleTypeInfo.VRN));
        }
    }

    @Test
    public void testMultipleVrnValuesDetection() {
        String url = "api/vehicles";
        String method = "GET";
        int responseCode = 200;

        Map<SensitiveParamInfo, Boolean> sensitiveParamInfoBooleanMap = new HashMap<>();

        KeyTypes keyTypes = new KeyTypes(new HashMap<>(), false);

        // Test multiple VRN values
        String[] vrnValues = {"AB12XYZ", "BD51SMR", "LA51ABC", "KA02HHH"};

        for (int i = 0; i < vrnValues.length; i++) {
            keyTypes.process(url, method, responseCode, false, "registration", vrnValues[i],
                    "u" + i, 0, "rawMessage", sensitiveParamInfoBooleanMap, false, Context.now());
        }

        assertTrue("VRN should be detected", keyTypes.occurrences.containsKey(SingleTypeInfo.VRN));

        SingleTypeInfo vrnTypeInfo = keyTypes.occurrences.get(SingleTypeInfo.VRN);
        assertNotNull("VRN type info should not be null", vrnTypeInfo);
        Set<String> detectedVrns = vrnTypeInfo.getValues().getElements();
        assertNotNull("Detected VRNs should not be null", detectedVrns);

        for (String vrn : vrnValues) {
            assertTrue("VRN " + vrn + " should be in detected values", detectedVrns.contains(vrn));
        }
    }

    @Test
    public void testVrnSubTypeProperties() {
        // Test that VRN SubType has correct properties
        assertNotNull("VRN SubType should exist", SingleTypeInfo.VRN);
        assertEquals("VRN SubType name should be 'VRN'", "VRN", SingleTypeInfo.VRN.getName());
        assertTrue("VRN should be marked as sensitive", SingleTypeInfo.VRN.isSensitiveAlways());

        // Verify VRN is in subTypeMap
        assertTrue("VRN should be in subTypeMap", SingleTypeInfo.subTypeMap.containsKey("VRN"));
        assertEquals("VRN in subTypeMap should match VRN SubType",
                SingleTypeInfo.VRN, SingleTypeInfo.subTypeMap.get("VRN"));
    }
}
