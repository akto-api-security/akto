package com.akto.dto.mcp;

import java.util.Map;

import com.akto.dao.test_editor.filter.ConfigParser;
import com.akto.dao.test_editor.info.InfoParser;
import com.akto.dto.test_editor.ConfigParserResult;
import com.akto.dto.test_editor.ExecutorConfigParserResult;
import com.akto.dto.test_editor.Info;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

public class MCPGuardrailConfigYamlParser {

    public static MCPGuardrailConfig parseTemplate(String content) throws Exception {

        ObjectMapper mapper = new ObjectMapper(new YAMLFactory());

        Map<String, Object> config = mapper.readValue(content, new TypeReference<Map<String, Object>>() {
        });
        return parseConfig(config);
    }

    public static MCPGuardrailConfig parseConfig(Map<String, Object> config) throws Exception {

        MCPGuardrailConfig guardrailConfig = null;

        String id = (String) config.get(MCPGuardrailConfig.ID);
        if (id == null) {
            return guardrailConfig;
        }

        Object filterMap = config.get(MCPGuardrailConfig.FILTER);
        if (filterMap == null) {
            // todo: should not be null, throw error
            guardrailConfig = new MCPGuardrailConfig(id, null);
        }

        ConfigParser configParser = new ConfigParser();
        ConfigParserResult filters = configParser.parse(filterMap);
        if (filters == null) {
            // todo: throw error
            guardrailConfig = new MCPGuardrailConfig(id, null);
        } else {
            guardrailConfig = new MCPGuardrailConfig(id, filters);
        }


        com.akto.dao.test_editor.executor.ConfigParser executorConfigParser = new com.akto.dao.test_editor.executor.ConfigParser();
        Object executionMap = config.get("execute");
        if(executionMap != null){
            ExecutorConfigParserResult executorConfigParserResult = executorConfigParser.parseConfigMap(executionMap);
            guardrailConfig.setExecutor(executorConfigParserResult);
        }

        InfoParser infoParser = new InfoParser();
        if (config.containsKey("info")) {
            Info info = infoParser.parse(config.get("info"));
            guardrailConfig.setInfo(info);
        }
        return guardrailConfig;

    }

}
