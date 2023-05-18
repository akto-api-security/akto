package com.akto.dao.test_editor.auth;

import java.util.Map;

import com.akto.dto.test_editor.Auth;
import com.fasterxml.jackson.databind.ObjectMapper;

public class Parser {
    
    public Auth parse(Object authObj) {
        Map<String, Object> infoMap = (Map) authObj;
        ObjectMapper objectMapper = new ObjectMapper();
        Auth info = objectMapper.convertValue(infoMap, Auth.class);
        return info;
    }

}
