package com.akto.dao.test_editor.settings;

import com.akto.dto.test_editor.TemplateSettings;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.Map;

public class SettingsParser {

    public TemplateSettings parse(Object settingsObj) {
        Map<String, Object> settingsMap = (Map) settingsObj;
        ObjectMapper objectMapper = new ObjectMapper();
        TemplateSettings settings = objectMapper.convertValue(settingsMap, TemplateSettings.class);
        return settings;
    }

}
