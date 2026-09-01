package com.akto.dto.type;

import com.akto.dao.context.Context;
import com.akto.dto.AktoDataType;
import com.akto.dto.CustomDataType;
import com.akto.dto.IgnoreData;
import com.akto.dto.SensitiveParamInfo;
import com.akto.dto.data_types.*;
import com.akto.detection.DetectionCorrectorRegistry;
import org.junit.Test;

import java.util.*;

import static org.junit.Assert.*;

public class TestKeyTypes {

    private final int ACCOUNT_ID = 1_000_000;

    public void testInitializer() {
        Context.accountId.set(ACCOUNT_ID);
        Context.setActualAccountId(ACCOUNT_ID);
        Map<String, AktoDataType> aktoDataTypeMap = new HashMap<>();
        aktoDataTypeMap.put("JWT", new AktoDataType(null, false, null, 0, new IgnoreData(new HashMap<>(), new HashSet<>()), false, true));
        aktoDataTypeMap.put("PHONE_NUMBER", new AktoDataType(null, false, null, 0, new IgnoreData(new HashMap<>(), new HashSet<>()), false, true));
        aktoDataTypeMap.put("CREDIT_CARD", new AktoDataType(null, false, null, 0, new IgnoreData(new HashMap<>(), new HashSet<>()), false, true));
        aktoDataTypeMap.put("IP_ADDRESS", new AktoDataType(null, false, null, 0, new IgnoreData(new HashMap<>(), new HashSet<>()), false, true));
        aktoDataTypeMap.put("EMAIL", new AktoDataType(null, false, null, 0, new IgnoreData(new HashMap<>(), new HashSet<>()), false, true));
        aktoDataTypeMap.put("SSN", new AktoDataType(null, false, null, 0, new IgnoreData(new HashMap<>(), new HashSet<>()), false, true));
        aktoDataTypeMap.put("UUID", new AktoDataType(null, false, null, 0, new IgnoreData(new HashMap<>(), new HashSet<>()), false, true));
        AccountDataTypesInfo info = SingleTypeInfo.getAccountToDataTypesInfo().get(ACCOUNT_ID);
        if (info == null) {
            info = new AccountDataTypesInfo();
        }
        info.setAktoDataTypeMap(aktoDataTypeMap);
        SingleTypeInfo.getAccountToDataTypesInfo().put(ACCOUNT_ID, info);
    }

    @Test
    public void testProcess() {
        Context.accountId.set(ACCOUNT_ID);
        testInitializer();
        String url = "url";
        String method = "GET";
        int responseCode = 200;

        Map<SensitiveParamInfo, Boolean> sensitiveParamInfoBooleanMap = new HashMap<>();
        SensitiveParamInfo sensitiveParamInfo1 = new SensitiveParamInfo(
                url, method, responseCode, false, "param1", 0, true
        );

        KeyTypes keyTypes = new KeyTypes(new HashMap<>(), false);
        HashMap<String, CustomDataType> customDataTypeMap = new HashMap<>();
        IgnoreData ignoreData = new IgnoreData(new HashMap<>(), new HashSet<>());
        CustomDataType customDataType1 = new CustomDataType("SHIPPING", true, Collections.emptyList(),
                1, true, new Conditions(Collections.singletonList(new StartsWithPredicate("ship")), Conditions.Operator.AND), null, Conditions.Operator.AND, ignoreData,  false, true);
        CustomDataType customDataType2 = new CustomDataType("CAPTAIN", false, Collections.emptyList(),
                1, true, new Conditions(Collections.singletonList(new StartsWithPredicate("captain")), Conditions.Operator.AND), null, Conditions.Operator.AND, ignoreData, false, true);

        customDataTypeMap.put("SHIPPING", customDataType1);
        customDataTypeMap.put("CAPTAIN", customDataType2);
        List<CustomDataType> customDataTypesSortedBySensitivity = new ArrayList<>();
        customDataTypesSortedBySensitivity.add(customDataType1);
        customDataTypesSortedBySensitivity.add(customDataType2);
        AccountDataTypesInfo info = SingleTypeInfo.getAccountToDataTypesInfo().get(ACCOUNT_ID);
        if (info == null) {
            info = new AccountDataTypesInfo();
        }
        info.setCustomDataTypeMap(customDataTypeMap);
        info.setCustomDataTypesSortedBySensitivity(customDataTypesSortedBySensitivity);
        SingleTypeInfo.getAccountToDataTypesInfo().put(ACCOUNT_ID, info);

        // not sensitive
        keyTypes.process(url, method, responseCode, false, "param1", "value1",
                "u1" ,0 ,"rawMessage1" , sensitiveParamInfoBooleanMap, false, Context.now());

        assertEquals(keyTypes.occurrences.get(SingleTypeInfo.GENERIC).getExamples().size(), 0);

        // sensitive
        keyTypes.process(url, method, responseCode, false, "param1", "avneesh@akto.io",
                        "u1" ,0 ,"rawMessage2" , sensitiveParamInfoBooleanMap, false, Context.now());

        assertEquals(keyTypes.occurrences.get(SingleTypeInfo.EMAIL).getExamples().size(), 1);
        assertEquals(keyTypes.occurrences.get(SingleTypeInfo.GENERIC).getExamples().size(), 0);

        // sensitive repeat (shouldn't add more examples)
        keyTypes.process(url, method, responseCode, false, "param1", "avneesh@akto.io",
                "u1" ,0 ,"rawMessage3" , sensitiveParamInfoBooleanMap, false, Context.now());

        assertEquals(keyTypes.occurrences.get(SingleTypeInfo.EMAIL).getExamples().size(), 1);

        // custom data type normal
        keyTypes.process(url, method, responseCode, false, "captain_id", "Kirk",
                "u1" ,0 ,"rawMessage3" , sensitiveParamInfoBooleanMap, false, Context.now());

        assertEquals(keyTypes.occurrences.get(customDataType2.toSubType()).getExamples().size(), 0);

        // custom data type sensitive
        keyTypes.process(url, method, responseCode, false, "ship_id", "NCC-1701",
                "u1" ,0 ,"rawMessage3" , sensitiveParamInfoBooleanMap, false, Context.now());

        assertEquals(keyTypes.occurrences.get(customDataType1.toSubType()).getExamples().size(), 1);

        // custom marked sensitive
        sensitiveParamInfoBooleanMap.put(sensitiveParamInfo1, false);
        keyTypes.process(url, method, responseCode, false, "param1", "value1",
                "u1" ,0 ,"rawMessage1" , sensitiveParamInfoBooleanMap, false, Context.now());

        assertEquals(keyTypes.occurrences.get(SingleTypeInfo.GENERIC).getExamples().size(), 1);
        assertTrue(sensitiveParamInfoBooleanMap.get(sensitiveParamInfo1));

        // custom marked sensitive repeat (shouldn't add more examples)
        sensitiveParamInfoBooleanMap.put(sensitiveParamInfo1, false);
        keyTypes.process(url, method, responseCode, false, "param1", "value1",
                "u1" ,0 ,"rawMessage1" , sensitiveParamInfoBooleanMap, false, Context.now());

        assertEquals(keyTypes.occurrences.get(SingleTypeInfo.GENERIC).getExamples().size(), 1);
        assertTrue(sensitiveParamInfoBooleanMap.get(sensitiveParamInfo1));

    }

    /**
     * process() must stay exactly equivalent to detect() + record(), so the split is safe for the
     * callers that still use the original single-call signature.
     */
    @Test
    public void testDetectAndRecordMatchesProcess() {
        testInitializer();
        String url = "url";
        String method = "GET";
        int responseCode = 200;
        int ts = Context.now();

        KeyTypes viaProcess = new KeyTypes(new HashMap<>(), false);
        viaProcess.process(url, method, responseCode, false, "email", "avneesh@akto.io",
                "u1", 0, "rawMessage1", new HashMap<>(), false, ts);

        KeyTypes viaSplit = new KeyTypes(new HashMap<>(), false);
        SingleTypeInfo.SubType detected = KeyTypes.detect(url, method, responseCode, false, "email", "avneesh@akto.io", 0, false);
        viaSplit.record(url, method, responseCode, false, "email", "avneesh@akto.io",
                "u1", 0, "rawMessage1", new HashMap<>(), false, ts, detected);

        assertEquals(SingleTypeInfo.EMAIL, detected);
        assertEquals(viaProcess.occurrences.keySet(), viaSplit.occurrences.keySet());

        SingleTypeInfo a = viaProcess.occurrences.get(SingleTypeInfo.EMAIL);
        SingleTypeInfo b = viaSplit.occurrences.get(SingleTypeInfo.EMAIL);
        assertEquals(a.getSubTypeString(), b.getSubTypeString());
        assertEquals(a.getCount(), b.getCount());
        assertEquals(a.getExamples(), b.getExamples());
        assertEquals(a.getParam(), b.getParam());
        assertEquals(a.getUrl(), b.getUrl());
    }

    /**
     * Differential check: with no corrector installed, the split path must produce byte-identical
     * occurrences to the original process() for every shape of value the runtime sees. This is the
     * guarantee that customers who never enable the feature are unaffected.
     */
    @Test
    public void splitPathMatchesProcessForEveryValueShape() {
        testInitializer();
        DetectionCorrectorRegistry.reset();   // feature off, as it is for every account by default

        Object[][] cases = {
            {"email",       "avneesh@akto.io"},
            {"email",       "not-an-email"},
            {"credit_card", "378282246310005"},
            {"count",       42},
            {"count",       9999999999L},
            {"ratio",       1.5f},
            {"flag",        Boolean.TRUE},
            {"flag",        Boolean.FALSE},
            {"missing",     null},
            {"id",          "550e8400-e29b-41d4-a716-446655440000"},
            {"link",        "https://example.com/a?b=c"},
            {"ship_id",     "NCC-1701"},
            {"captain_id",  "Kirk"},
            {"phone",       "+14155552671"},
            {"ip",          "192.168.1.1"},
            {"nested#deep#field", "value"},
            {"arr_queryParam", "q"},
        };

        for (boolean isHeader : new boolean[]{false, true}) {
            for (boolean isUrlParam : new boolean[]{false, true}) {
                for (Object[] c : cases) {
                    String param = (String) c[0];
                    Object value = c[1];
                    int ts = Context.now();

                    KeyTypes viaProcess = new KeyTypes(new HashMap<>(), false);
                    viaProcess.process("u", "GET", 200, isHeader, param, value,
                            "u1", 7, "raw", new HashMap<>(), isUrlParam, ts);

                    KeyTypes viaSplit = new KeyTypes(new HashMap<>(), false);
                    SingleTypeInfo.SubType st = KeyTypes.detect("u", "GET", 200, isHeader, param, value, 7, isUrlParam);
                    viaSplit.record("u", "GET", 200, isHeader, param, value,
                            "u1", 7, "raw", new HashMap<>(), isUrlParam, ts, st);

                    String where = param + "=" + value + " header=" + isHeader + " urlParam=" + isUrlParam;
                    assertEquals(where, viaProcess.occurrences.keySet(), viaSplit.occurrences.keySet());
                    for (SingleTypeInfo.SubType k : viaProcess.occurrences.keySet()) {
                        SingleTypeInfo a = viaProcess.occurrences.get(k);
                        SingleTypeInfo b = viaSplit.occurrences.get(k);
                        assertEquals(where, a.getSubTypeString(), b.getSubTypeString());
                        assertEquals(where, a.getCount(), b.getCount());
                        assertEquals(where, a.getExamples(), b.getExamples());
                        assertEquals(where, a.getParam(), b.getParam());
                        assertEquals(where, a.getUrl(), b.getUrl());
                        assertEquals(where, a.getIsHeader(), b.getIsHeader());
                        assertEquals(where, a.getIsUrlParam(), b.getIsUrlParam());
                        assertEquals(where, a.getResponseCode(), b.getResponseCode());
                        assertEquals(where, a.getApiCollectionId(), b.getApiCollectionId());
                        assertEquals(where, a.getMinValue(), b.getMinValue());
                        assertEquals(where, a.getMaxValue(), b.getMaxValue());
                        assertEquals(where, a.getValues().getElements(), b.getValues().getElements());
                    }
                }
            }
        }
    }

    /**
     * The point of the split: a caller can record a value under a subtype that local detection would
     * never have produced. Mirrors what the external classifier does when it refines EMAIL into a
     * more specific label.
     */
    @Test
    public void testRecordHonoursOverriddenSubType() {
        testInitializer();
        String url = "url";
        String method = "GET";
        int responseCode = 200;

        // An "externally assigned" data type: no key or value conditions at all.
        CustomDataType externalType = new CustomDataType("VERIFIED_CUSTOMER_EMAIL", true, Collections.emptyList(),
                1, true, null, null, Conditions.Operator.AND,
                new IgnoreData(new HashMap<>(), new HashSet<>()), false, true);

        AccountDataTypesInfo info = SingleTypeInfo.getAccountToDataTypesInfo().get(ACCOUNT_ID);
        info.setCustomDataTypeMap(Collections.singletonMap("VERIFIED_CUSTOMER_EMAIL", externalType));
        info.setCustomDataTypesSortedBySensitivity(Collections.singletonList(externalType));

        // A condition-less custom type can never match locally, so detection still says EMAIL.
        SingleTypeInfo.SubType detected = KeyTypes.detect(url, method, responseCode, false, "email", "john@gmail.com", 0, false);
        assertEquals(SingleTypeInfo.EMAIL, detected);

        // Recording under the externally-supplied subtype instead.
        KeyTypes keyTypes = new KeyTypes(new HashMap<>(), false);
        keyTypes.record(url, method, responseCode, false, "email", "john@gmail.com",
                "u1", 0, "rawMessage1", new HashMap<>(), false, Context.now(), externalType.toSubType());

        assertFalse(keyTypes.occurrences.containsKey(SingleTypeInfo.EMAIL));
        assertTrue(keyTypes.occurrences.containsKey(externalType.toSubType()));

        SingleTypeInfo sti = keyTypes.occurrences.get(externalType.toSubType());
        assertEquals("VERIFIED_CUSTOMER_EMAIL", sti.getSubTypeString());
        assertEquals(1, sti.getCount());
        // sensitiveAlways on the external type, so the raw message is captured as an example.
        assertEquals(1, sti.getExamples().size());
    }

    @Test
    public void testCustomAktoDataTypeEmail() {
        testInitializer();
        String url = "url";
        String method = "GET";
        int responseCode = 200;
        Map<SensitiveParamInfo, Boolean> sensitiveParamInfoBooleanMap = new HashMap<>();

        KeyTypes keyTypes = new KeyTypes(new HashMap<>(), false);
        keyTypes.process(url, method, responseCode, false, "email", "user@akto.io",
                "u1" ,0 ,"rawMessage1" , sensitiveParamInfoBooleanMap, false, Context.now());

        // this is a valid email according to default akto condtions
        assertTrue(keyTypes.occurrences.containsKey(SingleTypeInfo.EMAIL));

        Map<String, AktoDataType> aktoDataTypeMap = SingleTypeInfo.getAktoDataTypeMap(ACCOUNT_ID);

        List<Predicate> emailKeyPredicateList = Arrays.asList(new StartsWithPredicate("email"), new EndsWithPredicate("id"));
        Conditions emailKeyConditions = new Conditions(emailKeyPredicateList, Conditions.Operator.AND);
        aktoDataTypeMap.get("EMAIL").setKeyConditions(emailKeyConditions);

        List<Predicate> emailValuePredicateList = Arrays.asList(new ContainsPredicate("@"), new ContainsPredicate(".io"));
        Conditions emailValueConditions = new Conditions(emailValuePredicateList, Conditions.Operator.AND);
        aktoDataTypeMap.get("EMAIL").setValueConditions(emailValueConditions);

        aktoDataTypeMap.get("EMAIL").setOperator(Conditions.Operator.AND);

        keyTypes = new KeyTypes(new HashMap<>(), false);
        keyTypes.process(url, method, responseCode, false, "email", "user@akto.io",
                "u1" ,0 ,"rawMessage1" , sensitiveParamInfoBooleanMap, false, Context.now());


        // this is an invalid email according to user provided conditions
        assertFalse(keyTypes.occurrences.containsKey(SingleTypeInfo.EMAIL));

        keyTypes = new KeyTypes(new HashMap<>(), false);
        keyTypes.process(url, method, responseCode, false, "email_id", "user@akto.io",
                "u1" ,0 ,"rawMessage1" , sensitiveParamInfoBooleanMap, false, Context.now());

        // this is an valid email according to user provided conditions
        assertTrue(keyTypes.occurrences.containsKey(SingleTypeInfo.EMAIL));
    }

    @Test
    public void testCustomAktoDataTypeCreditCard() {
        testInitializer();
        String url = "url";
        String method = "GET";
        int responseCode = 200;
        Map<SensitiveParamInfo, Boolean> sensitiveParamInfoBooleanMap = new HashMap<>();

        KeyTypes keyTypes = new KeyTypes(new HashMap<>(), false);
        keyTypes.process(url, method, responseCode, false, "credit_card", "378282246310005",
                "u1" ,0 ,"rawMessage1" , sensitiveParamInfoBooleanMap, false, Context.now());

        // this is a valid credit card according to default akto condtions
        assertTrue(keyTypes.occurrences.containsKey(SingleTypeInfo.CREDIT_CARD));

        Map<String, AktoDataType> aktoDataTypeMap = SingleTypeInfo.getAktoDataTypeMap(ACCOUNT_ID);

        List<Predicate> creditCardKeyPredicateList = Arrays.asList(new ContainsPredicate("credit"), new ContainsPredicate("card"));
        Conditions creditCardKeyConditions = new Conditions(creditCardKeyPredicateList, Conditions.Operator.OR);
        aktoDataTypeMap.get("CREDIT_CARD").setKeyConditions(creditCardKeyConditions);

        List<Predicate> creditCardValuePredicateList = Arrays.asList(new StartsWithPredicate("card_"), new EndsWithPredicate("_amex"));
        Conditions creditCardValueConditions = new Conditions(creditCardValuePredicateList, Conditions.Operator.AND);
        aktoDataTypeMap.get("CREDIT_CARD").setValueConditions(creditCardValueConditions);

        aktoDataTypeMap.get("CREDIT_CARD").setOperator(Conditions.Operator.AND);

        keyTypes = new KeyTypes(new HashMap<>(), false);
        keyTypes.process(url, method, responseCode, false, "credit_card", "378282246310005",
                "u1" ,0 ,"rawMessage1" , sensitiveParamInfoBooleanMap, false, Context.now());


        // this is an invalid credit card according to user provided conditions
        assertFalse(keyTypes.occurrences.containsKey(SingleTypeInfo.CREDIT_CARD));

        keyTypes = new KeyTypes(new HashMap<>(), false);
        keyTypes.process(url, method, responseCode, false, "credit_card", "card_378282246310005_amex",
                "u1" ,0 ,"rawMessage1" , sensitiveParamInfoBooleanMap, false, Context.now());

        // this is an valid email according to user provided conditions
        assertTrue(keyTypes.occurrences.containsKey(SingleTypeInfo.CREDIT_CARD));
    }
}
