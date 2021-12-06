package com.akto.dto.type;

import static org.junit.Assert.assertTrue;

import java.util.HashMap;
import java.util.Map;


import org.junit.Test;

public class TestRequestTemplate {
    
    public RequestTemplate createRequestTemplate(boolean populateResponse) {

        Map<String, KeyTypes> parameters = new HashMap<>();
        Map<String, KeyTypes> headers = new HashMap<>();
        Map<Integer, RequestTemplate> responseTemplates = new HashMap<>();

        parameters.put("p1", new KeyTypes(new HashMap<>(), false));
        parameters.put("p2", new KeyTypes(new HashMap<>(), false));
        parameters.put("p3", new KeyTypes(new HashMap<>(), false));

        headers.put("h1", new KeyTypes(new HashMap<>(), false));
        headers.put("h2", new KeyTypes(new HashMap<>(), false));
        headers.put("h3", new KeyTypes(new HashMap<>(), false));

        if (populateResponse) {
            responseTemplates.put(200, createRequestTemplate(false));
        }

        return new RequestTemplate(parameters, responseTemplates, headers);
    }

    @Test
    public void testCompareKeys() {
        assertTrue(createRequestTemplate(true).compare(createRequestTemplate(true)));
        assertTrue(!createRequestTemplate(false).compare(createRequestTemplate(true)));
        assertTrue(!createRequestTemplate(false).compare(createRequestTemplate(false)));


        RequestTemplate a = createRequestTemplate(true);
        a.getHeaders().put("h4", new KeyTypes());
        assertTrue(!a.compare(createRequestTemplate(true)));
        assertTrue(!createRequestTemplate(true).compare(a));

    }

}
