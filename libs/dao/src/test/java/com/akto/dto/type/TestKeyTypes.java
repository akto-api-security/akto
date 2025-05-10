package com.akto.dto.type;

import com.akto.dao.context.Context;
import com.akto.dto.AktoDataType;
import com.akto.dto.CustomDataType;
import com.akto.dto.IgnoreData;
import com.akto.dto.SensitiveParamInfo;
import com.akto.dto.data_types.*;
import org.junit.Test;

import java.util.*;

import static org.junit.Assert.*;

public class TestKeyTypes {

    private final int ACCOUNT_ID = 1_000_000;

    public void testInitializer() {
        Context.accountId.set(ACCOUNT_ID);
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
