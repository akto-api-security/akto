package com.akto.dao.test_editor.metadata;

import java.util.Map;

import com.akto.dto.test_editor.Metadata;
import com.fasterxml.jackson.databind.ObjectMapper;

public class MetadataParser {
    
    public Metadata parse(Object metadataObj) {
        Map<String, Object> metadataMap = (Map) metadataObj;
        ObjectMapper objectMapper = new ObjectMapper();
        Metadata metadata = objectMapper.convertValue(metadataMap, Metadata.class);

        if (metadata.getIsActive() == null) {
            metadata.setIsActive(true);
        }

        return metadata;
    }

}
