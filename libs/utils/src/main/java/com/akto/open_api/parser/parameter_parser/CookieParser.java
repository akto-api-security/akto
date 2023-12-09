package com.akto.open_api.parser.parameter_parser;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.swagger.v3.oas.models.parameters.Parameter;

public class CookieParser {

    public static final String COOKIE = "cookie";

    public static Map<String, String> getCookieHeader(List<List<Parameter>> parametersList) {
        Map<String, String> cookieMap = new HashMap<>();

        for (List<Parameter> parameters : parametersList) {
            if (parameters != null) {
                for (Parameter parameter : parameters) {
                    if (parameter.getIn().equals(COOKIE)) {
                        cookieMap.put(parameter.getName(), parameter.getExample().toString());
                    }
                }
            }
        }

        if (cookieMap.isEmpty()) {
            return new HashMap<>();
        }

        String cookieString = "";
        for (String key : cookieMap.keySet()) {
            if (cookieString.isEmpty()) {
                cookieString = key + cookieMap.get(key);
            } else {
                cookieString += ";" + key + cookieMap.get(key);
            }
        }
        final String CookieString = cookieString;
        return new HashMap<String, String>() {
            {
                put(COOKIE, CookieString);
            }
        };
    }

}