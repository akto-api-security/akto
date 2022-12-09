package com.akto.utils;

import com.akto.dto.OriginalHttpRequest;
import com.akto.dto.type.RequestTemplate;
import com.akto.util.JSONUtils;
import com.akto.util.modifier.ConvertToArrayPayloadModifier;
import com.akto.util.modifier.NestedObjectModifier;
import com.mongodb.BasicDBObject;
import org.junit.Test;

import java.util.HashSet;
import java.util.Set;

public class TestJsonUtils {

    @Test
    public void testModify() {
        BasicDBObject payload = RequestTemplate.parseRequestPayload("{'user': {'name': 'avneesh'}, 'friends': [{'name':'ankush'}, {'name':'ankita'}, {'name':'shivam'}, {'name':'ayush'}]}", null);
        Set<String> s = new HashSet<>();
        s.add("friends#$#name");

        ConvertToArrayPayloadModifier convertToArrayPayloadModifier = new ConvertToArrayPayloadModifier();
        BasicDBObject modifiedPayload = JSONUtils.modify(payload, s, convertToArrayPayloadModifier);
        System.out.println(modifiedPayload);

        NestedObjectModifier nestedObjectModifier = new NestedObjectModifier();
        modifiedPayload = JSONUtils.modify(payload, s, nestedObjectModifier);
        System.out.println(modifiedPayload);
    }
}
