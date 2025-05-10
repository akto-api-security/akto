package com.akto.util.grpc;

import static org.junit.Assert.assertEquals;

import java.util.HashMap;
import java.util.Map;

import org.junit.Test;

import com.fasterxml.jackson.databind.JsonNode;

public class TestParameterTransformer {
    
    @Test
    public void testTransform() {
        Map<String, Object> params = new HashMap<>();
        
        // Test cases
        params.put("a#b", "value1");
        params.put("a#c#$#d", "value2");
        params.put("x#y#$#z", "value3");
        params.put("x#y#$#z", "value4");
        params.put("p#q#r#$#s#t", "value5");
        params.put("p#q#r#$#s#q#$#w", "value5");
        params.put("m#n", "value6");
        params.put("m#o", "value7");
        
        JsonNode result = ParameterTransformer.transform(params);
        
        JsonNode m = result.get("m");
        assertEquals(m.get("n").toString(), "\"value6\"");

        JsonNode a = result.get("a");
        JsonNode c = a.get("c");
        JsonNode firstElement = c.get(0);
        assertEquals(firstElement.get("d").toString(), "\"value2\"");

    }

    @Test
    public void testTransformKey(){

        String[] tests = {
            "keys$1#$#delegatable_contract_id$8#shardNum$2",
            "simple#key",
            "object$3#nested$2#field$1",
            "array#$#element$5",
            "$1#startsWith#type",
            "ends#with#type$8",
            "multiple$1#$#types$2#in$3#one$4#key"
        };

        String[] actual = {
            "keys#$#delegatable_contract_id#shardNum",
            "simple#key",
            "object#nested#field",
            "array#$#element",
            "#startsWith#type",
            "ends#with#type",
            "multiple#$#types#in#one#key"
        };

        for (int i = 0; i < tests.length; i++) {
            String transformed = ParameterTransformer.transformKey(tests[i]);
            assertEquals(transformed, actual[i]);
        }
    }

}
