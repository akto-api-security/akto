package com.akto.dto;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class TestApiInfo {

    @Test
    public void testCalculateActualAuth1() {
        ApiInfo apiInfo = new ApiInfo();
        apiInfo.setAllAuthTypesFound(new HashSet<>());

        Set<ApiInfo.AuthType> a = new HashSet<>();
        a.add(ApiInfo.AuthType.JWT);
        apiInfo.getAllAuthTypesFound().add(a);


        Set<ApiInfo.AuthType> b = new HashSet<>();
        b.add(ApiInfo.AuthType.BEARER);
        b.add(ApiInfo.AuthType.UNAUTHENTICATED);
        apiInfo.getAllAuthTypesFound().add(b);

        apiInfo.calculateActualAuth();

        assertEquals(apiInfo.getActualAuthType(), Collections.singletonList(ApiInfo.AuthType.UNAUTHENTICATED));
    }

    @Test
    public void testCalculateActualAuth2() {
        ApiInfo apiInfo = new ApiInfo();
        apiInfo.setAllAuthTypesFound(new HashSet<>());

        Set<ApiInfo.AuthType> a = new HashSet<>();
        a.add(ApiInfo.AuthType.JWT);
        a.add(ApiInfo.AuthType.BEARER);
        apiInfo.getAllAuthTypesFound().add(a);


        Set<ApiInfo.AuthType> b = new HashSet<>();
        b.add(ApiInfo.AuthType.BEARER);
        apiInfo.getAllAuthTypesFound().add(b);

        apiInfo.calculateActualAuth();

        assertEquals(2, apiInfo.getActualAuthType().size());
        assertTrue(apiInfo.getActualAuthType().containsAll( Arrays.asList(ApiInfo.AuthType.JWT , ApiInfo.AuthType.BEARER)));

    }

    @Test
    public void testCalculateActualAuth3() {
        ApiInfo apiInfo = new ApiInfo();
        apiInfo.setAllAuthTypesFound(new HashSet<>());

        Set<ApiInfo.AuthType> a = new HashSet<>();
        a.add(ApiInfo.AuthType.JWT);
        a.add(ApiInfo.AuthType.BEARER);
        apiInfo.getAllAuthTypesFound().add(a);

        Set<ApiInfo.AuthType> b = new HashSet<>();
        b.add(ApiInfo.AuthType.JWT);
        apiInfo.getAllAuthTypesFound().add(b);

        Set<ApiInfo.AuthType> c = new HashSet<>();
        c.add(ApiInfo.AuthType.BASIC);
        apiInfo.getAllAuthTypesFound().add(c);

        apiInfo.calculateActualAuth();

        assertTrue(apiInfo.getActualAuthType().containsAll(Arrays.asList(ApiInfo.AuthType.BASIC, ApiInfo.AuthType.JWT)));
    }
}
