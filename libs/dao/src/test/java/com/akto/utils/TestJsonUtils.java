package com.akto.utils;

import com.akto.dto.OriginalHttpRequest;
import com.akto.dto.type.RequestTemplate;
import com.akto.util.JSONUtils;
import com.akto.util.modifier.ConvertToArrayPayloadModifier;
import com.akto.util.modifier.NestedObjectModifier;
import com.mongodb.BasicDBObject;
import org.junit.Test;

import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

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
}
