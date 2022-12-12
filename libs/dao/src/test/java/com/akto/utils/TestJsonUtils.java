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
import java.util.Set;

import static org.junit.Assert.*;

public class TestJsonUtils {

    @Test
    public void testModify() {
        BasicDBObject payload = RequestTemplate.parseRequestPayload("{'user': {'name': 'avneesh'}, 'friends': [{'name':'ankush'}, {'name':'ankita'}, {'name':'shivam'}, {'name':'ayush'}]}", null);
        Set<String> s = new HashSet<>();
        s.add("friends#$#name");

        ConvertToArrayPayloadModifier convertToArrayPayloadModifier = new ConvertToArrayPayloadModifier();
        BasicDBObject modifiedPayload = JSONUtils.modify(payload, s, convertToArrayPayloadModifier);
        assertTrue(JSONUtils.flatten(modifiedPayload).get("friends#$#name").contains(Collections.singletonList("ankush")));
        assertTrue(JSONUtils.flatten(modifiedPayload).get("friends#$#name").contains(Collections.singletonList("ankita")));

        NestedObjectModifier nestedObjectModifier = new NestedObjectModifier();
        modifiedPayload = JSONUtils.modify(payload, s, nestedObjectModifier);
        assertTrue(JSONUtils.flatten(modifiedPayload).get("friends#$#name#name").contains("ankush"));
        assertTrue(JSONUtils.flatten(modifiedPayload).get("friends#$#name#name").contains("ankita"));
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
