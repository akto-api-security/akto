package com.akto.utils;

import com.akto.dto.type.RequestTemplate;
import com.akto.util.JSONUtils;
import com.akto.util.modifier.AddJkuJWTModifier;
import com.akto.util.modifier.ConvertToArrayPayloadModifier;
import com.akto.util.modifier.NestedObjectModifier;
import com.mongodb.BasicDBObject;
import org.junit.Test;

import java.util.*;

import static org.junit.Assert.*;

public class TestJsonUtils {

    @Test
    public void testModify() {
        NestedObjectModifier nestedObjectModifier = new NestedObjectModifier();
        ConvertToArrayPayloadModifier convertToArrayPayloadModifier = new ConvertToArrayPayloadModifier();

        String payload = "{'user': {'name': 'avneesh'}, 'friends': [{'name':'ankush'}, {'name':'ankita'}, {'name':'shivam'}, {'name':'ayush'}]}";
        Set<String> s = new HashSet<>();
        s.add("friends#$#name");

        String modifiedPayload = JSONUtils.modify(payload, s, convertToArrayPayloadModifier);
        BasicDBObject modifiedBasicDbObject = RequestTemplate.parseRequestPayload(modifiedPayload, null);
        Map<String, Set<Object>> flattened = JSONUtils.flatten(modifiedBasicDbObject);
        assertTrue(flattened.get("friends#$#name#$").contains("ankush"));
        assertTrue(flattened.get("friends#$#name#$").contains("ankita"));

        modifiedPayload = JSONUtils.modify(payload, s, nestedObjectModifier);
        modifiedBasicDbObject = RequestTemplate.parseRequestPayload(modifiedPayload, null);
        flattened = JSONUtils.flatten(modifiedBasicDbObject);
        assertTrue(flattened.get("friends#$#name#name").contains("ankush"));
        assertTrue(flattened.get("friends#$#name#name").contains("ankita"));


        s = new HashSet<>();
        s.add("json#$#name");

        payload = "[{'name':'ankush'}, {'name':'ankita'}, {'name':'shivam'}, {'name':'ayush'}]";
        modifiedPayload = JSONUtils.modify(payload, s, convertToArrayPayloadModifier);
        modifiedBasicDbObject = RequestTemplate.parseRequestPayload(modifiedPayload, null);
        flattened = JSONUtils.flatten(modifiedBasicDbObject);
        assertTrue(flattened.get("json#$#name#$").contains("ankush"));
        assertTrue(flattened.get("json#$#name#$").contains("ankita"));

        modifiedPayload = JSONUtils.modify(payload, s, nestedObjectModifier);
        modifiedBasicDbObject = RequestTemplate.parseRequestPayload(modifiedPayload, null);
        flattened = JSONUtils.flatten(modifiedBasicDbObject);
        assertTrue(flattened.get("json#$#name#name").contains("ankush"));
        assertTrue(flattened.get("json#$#name#name").contains("ankita"));
    }

    @Test
    public void testNonJsonModify() {
        String payload = "user=avneesh";
        Set<String> s = new HashSet<>();
        s.add("friends#$#name");
        ConvertToArrayPayloadModifier convertToArrayPayloadModifier = new ConvertToArrayPayloadModifier();
        String modifiedPayload = JSONUtils.modify(payload, s, convertToArrayPayloadModifier);
        assertNull(modifiedPayload);
    }

    @Test
    public void testModifyHeaderValues() {
        Map<String, List<String>> headers = new HashMap<>();
        headers.put("host", Collections.singletonList("akto.io"));
        headers.put("fake", Collections.singletonList("a.b.c"));

        Map<String, List<String>> result = JSONUtils.modifyHeaderValues(null, new AddJkuJWTModifier());
        assertNull(result);

        result = JSONUtils.modifyHeaderValues(new HashMap<>(), new AddJkuJWTModifier());
        assertNull(result);

        result = JSONUtils.modifyHeaderValues(headers, new AddJkuJWTModifier());
        assertNull(result);

        headers.put("token", Collections.singletonList("eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIxMjM0NTY3ODkwIiwibmFtZSI6IkpvaG4gRG9lIiwiYWRtaW4iOnRydWUsImlhdCI6MTUxNjIzOTAyMn0.NHVaYe26MbtOYhSKkoKYdFVomg4i8ZJd8_-RU8VNbftc4TSMb4bXP3l3YlNWACwyXPGffz5aXHc6lty1Y2t4SWRqGteragsVdZufDn5BlnJl9pdR_kdVFUsra2rWKEofkZeIC4yWytE58sMIihvo9H1ScmmVwBcQP6XETqYd0aSHp1gOa9RdUPDvoXQ5oqygTqVtxaDr6wUFKrKItgBMzWIdNZ6y7O9E0DhEPTbE9rfBo6KTFsHAZnMg4k68CDp2woYIaXbmYTWcvbzIuHO7_37GT79XdIwkm95QJ7hYC9RiwrV7mesbY4PAahERJawntho0my942XheVLmGwLMBkQ"));
        headers.put("anotherToken", Collections.singletonList("eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIxMjM0NTY3ODkwIiwibmFtZSI6IkpvaG4gRG9lIiwiYWRtaW4iOnRydWUsImlhdCI6MTUxNjIzOTAyMiwiZXhwIjoxNTE2MjM5MDkwfQ.qnloZdeBgTtHUtvyhpo37TaxF1CZOLRI0rztKoITjm0NLJBpRqHB9mryP_Zy4FgV8MXDJb0-83pwjqOh8bdIWQKGukQaEL-dwetXCv9N8rWu3yud1ETtgvSNf5_k7X4X02ZbXeOW-qG39E60xtqvPySZ0zlaHbttZfpYus7SrklFoPFZFtDvzAXamFuLjZ1DIrgrW0i7BHl2CmwyEJS2IB3vpYdJzeN0ONlw7WYuVGxfG7BHeyUl7fI51dZHzYEBxvR4o0bZHxRqEWPQDwqAmc2Nbf_LA9p1muJakxT8DgNMxb3cbwusKpes9Ff7seBy1_tFke5HTViB4tO__XOeNw"));

        result = JSONUtils.modifyHeaderValues(headers, new AddJkuJWTModifier());
        assertNotNull(result);
        assertEquals(headers.get("host"), result.get("host"));
        assertEquals(headers.get("fake"), result.get("fake"));
        assertNotEquals(headers.get("token"), result.get("token"));
        assertNotEquals(headers.get("anotherToken"), result.get("anotherToken"));

    }
}
