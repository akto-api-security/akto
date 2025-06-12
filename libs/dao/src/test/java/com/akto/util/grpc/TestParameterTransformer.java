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
        params.put("x#y#$#p", "value4");
        // override x#y#$#p with a different value
        params.put("x#y#$#p", "value5");
        params.put("p#q#r#$#s#t", "value5");
        params.put("p#q#r#$#s#q#$#w", "value5");
        params.put("m#n", "value6");
        params.put("m#o", "value7");
        params.put("first#second#third#$#fourthFirst#FifthOne", 1002);
        params.put("first#second#third#$#fourthFirst#FifthTwo", 0);
        params.put("first#second#third#$#fourthFirst#FifthThree", 1);
        params.put("first#second#third#$#fourthSecond", 10000);
        params.put("first#second#third#$#fourthThird#$#FifthFour", 10001);
        
        JsonNode result = ParameterTransformer.transform(params);
        
        // Check if value got updated for m#n and m#o
        JsonNode m = result.get("m");
        assertEquals("value6", m.get("n").asText());
        assertEquals("value7", m.get("o").asText());

        // Check for a#b and a#c#$#d
        JsonNode a = result.get("a");
        assertEquals("value1", a.get("b").asText());
        JsonNode c = a.get("c");
        JsonNode firstElement = c.get(0);
        assertEquals("value2", firstElement.get("d").asText());

        // Check for x#y#$#z and x#y#$#p merged in same array object
        JsonNode x = result.get("x");
        JsonNode y = x.get("y");
        JsonNode arrObj = y.get(0);
        assertEquals("value3", arrObj.get("z").asText());
        assertEquals("value5", arrObj.get("p").asText());

        // Check for objects with nested structure inside an array.
        JsonNode first = result.get("first");
        JsonNode second = first.get("second");
        JsonNode third = second.get("third");
        JsonNode arrObj2 = third.get(0);
        assertEquals(1002, arrObj2.get("fourthFirst").get("FifthOne").asInt());
        assertEquals(0, arrObj2.get("fourthFirst").get("FifthTwo").asInt());
        assertEquals(1, arrObj2.get("fourthFirst").get("FifthThree").asInt());
        assertEquals(10000, arrObj2.get("fourthSecond").asInt());
        assertEquals(10001, arrObj2.get("fourthThird").get(0).get("FifthFour").asInt());

        // Check for p#q#r#$#s#t and p#q#r#$#s#q#$#w
        JsonNode p = result.get("p");
        JsonNode q = p.get("q");
        JsonNode r = q.get("r");
        JsonNode arrObj3 = r.get(0);
        assertEquals("value5", arrObj3.get("s").get("t").asText());
        assertEquals("value5", arrObj3.get("s").get("q").get(0).get("w").asText());
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
            "multiple$1#$#types$2#in$3#one$4#key",
            "first$1#second$2#third$3#$#fourthFirst$4#FifthOne$5",
            "first$1#second$2#third$3#$#fourthSecond$4"
        };

        String[] actual = {
            "keys#$#delegatable_contract_id#shardNum",
            "simple#key",
            "object#nested#field",
            "array#$#element",
            "#startsWith#type",
            "ends#with#type",
            "multiple#$#types#in#one#key",
            "first#second#third#$#fourthFirst#FifthOne",
            "first#second#third#$#fourthSecond"
        };

        for (int i = 0; i < tests.length; i++) {
            String transformed = ParameterTransformer.transformKey(tests[i]);
            assertEquals(transformed, actual[i]);
        }
    }

}
