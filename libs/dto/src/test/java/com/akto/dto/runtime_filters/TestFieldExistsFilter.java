package com.akto.dto.runtime_filters;

import com.akto.dto.HttpResponseParams;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class TestFieldExistsFilter {

    @Test
    public void happy() {
        FieldExistsFilter fieldExistsFilter = new FieldExistsFilter("user_id");
        HttpResponseParams httpResponseParams1 = new HttpResponseParams();
        httpResponseParams1.setPayload("{ \"name\": \"akto\", \"car\": \"Merc\", \"user\": { \"user_id\": 1, \"name\": \"akto\", \"age\": 3} }");
        boolean result1 = fieldExistsFilter.process(httpResponseParams1);
        assertTrue(result1);

        httpResponseParams1.setPayload("{ \"name\": \"akto\", \"car\": \"Merc\", \"user\": { \"user\": 1, \"name\": \"akto\", \"age\": 3} }");
        boolean result2 = fieldExistsFilter.process(httpResponseParams1);
        assertFalse(result2);


        httpResponseParams1.setPayload("{ \"name\": \"akto\", \"car\": \"Merc\", \"user\": [{ \"userId\": 1, \"name\": \"akto\", \"age\": 3}, { \"userId\": 1, \"name\": \"akto\", \"age\": 3},{ \"user_id\": 1, \"name\": \"akto\", \"age\": 3}] }");
        boolean result3 = fieldExistsFilter.process(httpResponseParams1);
        assertTrue(result3);

        httpResponseParams1.setPayload("{ \"name\": \"akto\", \"car\": \"Merc\", \"user_id\": { \"user\": 1, \"name\": \"akto\", \"age\": 3} }");
        boolean result4 = fieldExistsFilter.process(httpResponseParams1);
        assertTrue(result4);

        httpResponseParams1.setPayload("{ \"name\": \"akto\", \"car\": \"Merc\", \"user\": [[{ \"userId\": 1, \"name\": \"akto\", \"age\": 3}, { \"userId\": 1, \"name\": \"akto\", \"age\": 3},{ \"user_id\": 1, \"name\": \"akto\", \"age\": 3}]] }");
        boolean result5 = fieldExistsFilter.process(httpResponseParams1);
        assertTrue(result5);

        httpResponseParams1.setPayload("[{ \"name\": \"akto\", \"car\": \"Merc\", \"user\": [[{ \"userId\": 1, \"name\": \"akto\", \"age\": 3}, { \"userId\": 1, \"name\": \"akto\", \"age\": 3},{ \"user_id\": 1, \"name\": \"akto\", \"age\": 3}]] }]");
        boolean result6 = fieldExistsFilter.process(httpResponseParams1);
        assertTrue(result6);
    }
}
