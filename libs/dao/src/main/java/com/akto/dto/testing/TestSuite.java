package com.akto.dto.testing;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public enum TestSuite {

    BUSINESS_LOGIC("Business logic", "Business logic tests", Arrays.asList("REPLACE_AUTH_TOKEN", "ADD_USER_ID", "CHANGE_METHOD", "REMOVE_TOKENS", "PARAMETER_POLLUTION", "REPLACE_AUTH_TOKEN_OLD_VERSION", "JWT_NONE_ALGO", "JWT_INVALID_SIGNATURE", "JWT_INVALID_SIGNATURE", "ADD_JKU_TO_JWT", "OPEN_REDIRECT", "PAGINATION_MISCONFIGURATION")),
    PASSIVE_SCAN("Passive scan", "Passive scan your APIs", new ArrayList<>()),
    DEEP_SCAN("Deep scan", "Deep scan your APIs", new ArrayList<>());

    public final String name;
    public final String description;
    public final List<String> tests;

    TestSuite(String name, String description, List<String> tests) {
        this.name = name;
        this.description = description;
        this.tests = tests;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public List<String> getTests() {
        return tests;
    }

    


}
