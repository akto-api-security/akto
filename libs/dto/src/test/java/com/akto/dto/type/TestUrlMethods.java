package com.akto.dto.type;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class TestUrlMethods {

    @Test
    public void testFromString() {
        URLMethods.Method method = URLMethods.Method.fromString("Get");
        assertEquals(URLMethods.Method.GET, method);

        method = URLMethods.Method.fromString("gEt");
        assertEquals(URLMethods.Method.GET, method);

        method = URLMethods.Method.fromString("PATCH");
        assertEquals(URLMethods.Method.PATCH, method);

        method = URLMethods.Method.fromString("Avneesh");
        assertEquals(URLMethods.Method.OTHER, method);

        method = URLMethods.Method.fromString(null);
        assertEquals(URLMethods.Method.OTHER, method);
    }
}
