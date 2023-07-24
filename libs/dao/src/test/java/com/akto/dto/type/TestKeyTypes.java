package com.akto.dto.type;
import com.akto.dao.context.Context;
import com.akto.dto.AktoDataType;
import com.akto.dto.CustomDataType;
import com.akto.dto.IgnoreData;
import com.akto.dto.SensitiveParamInfo;
import com.akto.dto.data_types.Conditions;
import com.akto.dto.data_types.StartsWithPredicate;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class TestKeyTypes {

public void testInitializer(){
        SingleTypeInfo.aktoDataTypeMap = new HashMap<>();
        SingleTypeInfo.aktoDataTypeMap.put("JWT", new AktoDataType(null, false, null, 0, new IgnoreData(new HashMap<>(), new HashSet<>())));
        SingleTypeInfo.aktoDataTypeMap.put("PHONE_NUMBER", new AktoDataType(null, false, null, 0, new IgnoreData(new HashMap<>(), new HashSet<>())));
        SingleTypeInfo.aktoDataTypeMap.put("CREDIT_CARD", new AktoDataType(null, false, null, 0, new IgnoreData(new HashMap<>(), new HashSet<>())));
        SingleTypeInfo.aktoDataTypeMap.put("IP_ADDRESS", new AktoDataType(null, false, null, 0, new IgnoreData(new HashMap<>(), new HashSet<>())));
        SingleTypeInfo.aktoDataTypeMap.put("EMAIL", new AktoDataType(null, false, null, 0, new IgnoreData(new HashMap<>(), new HashSet<>())));
        SingleTypeInfo.aktoDataTypeMap.put("SSN", new AktoDataType(null, false, null, 0, new IgnoreData(new HashMap<>(), new HashSet<>())));
        SingleTypeInfo.aktoDataTypeMap.put("UUID", new AktoDataType(null, false, null, 0, new IgnoreData(new HashMap<>(), new HashSet<>())));
        }

    @Test
    public void testProcess() {
        testInitializer();
        String url = "url";
        String method = "GET";
        int responseCode = 200;

        Map<SensitiveParamInfo, Boolean> sensitiveParamInfoBooleanMap = new HashMap<>();
        SensitiveParamInfo sensitiveParamInfo1 = new SensitiveParamInfo(
                url, method, responseCode, false, "param1", 0, true
        );

        KeyTypes keyTypes = new KeyTypes(new HashMap<>(),false);
        SingleTypeInfo.customDataTypeMap = new HashMap<>();
        IgnoreData ignoreData = new IgnoreData(new HashMap<>(), new HashSet<>());
        CustomDataType customDataType1 = new CustomDataType("SHIPPING", true, Collections.emptyList(),
                1, true,new Conditions(Collections.singletonList(new StartsWithPredicate("ship")), Conditions.Operator.AND),null, Conditions.Operator.AND,ignoreData);
        CustomDataType customDataType2 = new CustomDataType("CAPTAIN",false, Collections.emptyList(),
                1,true,new Conditions(Collections.singletonList(new StartsWithPredicate("captain")), Conditions.Operator.AND),null, Conditions.Operator.AND,ignoreData);

        SingleTypeInfo.customDataTypeMap.put("SHIPPING", customDataType1);
        SingleTypeInfo.customDataTypeMap.put("CAPTAIN", customDataType2);
        SingleTypeInfo.customDataTypesSortedBySensitivity = new ArrayList<>();
        SingleTypeInfo.customDataTypesSortedBySensitivity.add(customDataType1);
        SingleTypeInfo.customDataTypesSortedBySensitivity.add(customDataType2);

        // not sensitive
        keyTypes.process(url,method,responseCode ,false ,"param1" ,"value1" ,
                "u1" ,0 ,"rawMessage1" , sensitiveParamInfoBooleanMap, false, Context.now());

        assertEquals(keyTypes.occurrences.get(SingleTypeInfo.GENERIC).getExamples().size(), 0);

        // sensitive
        keyTypes.process(url,method,responseCode ,false ,"param1" ,"avneesh@akto.io" ,
                        "u1" ,0 ,"rawMessage2" , sensitiveParamInfoBooleanMap, false, Context.now());

        assertEquals(keyTypes.occurrences.get(SingleTypeInfo.EMAIL).getExamples().size(), 1);
        assertEquals(keyTypes.occurrences.get(SingleTypeInfo.GENERIC).getExamples().size(), 0);

        // sensitive repeat (shouldn't add more examples)
        keyTypes.process(url,method,responseCode ,false ,"param1" ,"avneesh@akto.io" ,
                "u1" ,0 ,"rawMessage3" , sensitiveParamInfoBooleanMap, false, Context.now());

        assertEquals(keyTypes.occurrences.get(SingleTypeInfo.EMAIL).getExamples().size(), 1);

        // custom data type normal
        keyTypes.process(url,method,responseCode ,false ,"captain_id" ,"Kirk" ,
                "u1" ,0 ,"rawMessage3" , sensitiveParamInfoBooleanMap, false, Context.now());

        assertEquals(keyTypes.occurrences.get(customDataType2.toSubType()).getExamples().size(), 0);

        // custom data type sensitive
        keyTypes.process(url,method,responseCode ,false ,"ship_id" ,"NCC-1701" ,
                "u1" ,0 ,"rawMessage3" , sensitiveParamInfoBooleanMap, false, Context.now());

        assertEquals(keyTypes.occurrences.get(customDataType1.toSubType()).getExamples().size(), 1);

        // custom marked sensitive
        sensitiveParamInfoBooleanMap.put(sensitiveParamInfo1, false);
        keyTypes.process(url,method,responseCode ,false ,"param1" ,"value1" ,
                "u1" ,0 ,"rawMessage1" , sensitiveParamInfoBooleanMap, false, Context.now());

        assertEquals(keyTypes.occurrences.get(SingleTypeInfo.GENERIC).getExamples().size(), 1);
        assertTrue(sensitiveParamInfoBooleanMap.get(sensitiveParamInfo1));

        // custom marked sensitive repeat (shouldn't add more examples)
        sensitiveParamInfoBooleanMap.put(sensitiveParamInfo1, false);
        keyTypes.process(url,method,responseCode ,false ,"param1" ,"value1" ,
                "u1" ,0 ,"rawMessage1" , sensitiveParamInfoBooleanMap, false, Context.now());

        assertEquals(keyTypes.occurrences.get(SingleTypeInfo.GENERIC).getExamples().size(), 1);
        assertTrue(sensitiveParamInfoBooleanMap.get(sensitiveParamInfo1));

    }
 }
