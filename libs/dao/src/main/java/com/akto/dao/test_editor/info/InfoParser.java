package com.akto.dao.test_editor.info;

import java.util.Map;

import com.akto.dto.test_editor.Info;
import com.fasterxml.jackson.databind.ObjectMapper;

public class InfoParser {
    
    public Info parse(Object infoObj) {
        Map<String, Object> infoMap = (Map) infoObj;
        ObjectMapper objectMapper = new ObjectMapper();
        Info info = objectMapper.convertValue(infoMap, Info.class);
        return info;
    }

}
