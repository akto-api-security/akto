package com.akto.dao.monitoring;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import com.akto.dao.AccountsContextDao;
import com.akto.dao.context.Context;
import com.akto.dto.monitoring.FilterConfig;
import com.akto.dto.test_editor.YamlTemplate;
import com.akto.util.Pair;
import com.akto.util.enums.GlobalEnums.CONTEXT_SOURCE;
import com.mongodb.client.model.Filters;

public class FilterYamlTemplateDao extends AccountsContextDao<YamlTemplate> {

    public static final FilterYamlTemplateDao instance = new FilterYamlTemplateDao();
    private static final ConcurrentHashMap<Pair<Integer, CONTEXT_SOURCE>, Pair<Set<String>, Integer>> contextFiltersMap = new ConcurrentHashMap<>();

    public Map<String, FilterConfig> fetchFilterConfig(
            boolean includeYamlContent, boolean shouldParseExecutor) {
        List<YamlTemplate> yamlTemplates = FilterYamlTemplateDao.instance.findAll(Filters.empty());
        return fetchFilterConfig(includeYamlContent, yamlTemplates, shouldParseExecutor);
    }

    public static Map<String, FilterConfig> fetchFilterConfig(
            boolean includeYamlContent,
            List<YamlTemplate> yamlTemplates,
            boolean shouldParseExecutor) {
        Map<String, FilterConfig> filterConfigMap = new HashMap<>();
        for (YamlTemplate yamlTemplate : yamlTemplates) {
            try {
                if (yamlTemplate != null) {
                    FilterConfig filterConfig =
                            FilterConfigYamlParser.parseTemplate(
                                    yamlTemplate.getContent(), shouldParseExecutor);
                    filterConfig.setAuthor(yamlTemplate.getAuthor());
                    filterConfig.setCreatedAt(yamlTemplate.getCreatedAt());
                    filterConfig.setUpdatedAt(yamlTemplate.getUpdatedAt());
                    if (includeYamlContent) {
                        filterConfig.setContent(yamlTemplate.getContent());
                    }
                    filterConfigMap.put(filterConfig.getId(), filterConfig);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return filterConfigMap;
    }

    public static Set<String> getContextTemplatesForAccount(int accountId, CONTEXT_SOURCE source) {
        if(source == null) {
            source = CONTEXT_SOURCE.API;
        }
        Pair<Integer, CONTEXT_SOURCE> key = new Pair<>(accountId, source);
        Pair<Set<String>, Integer> collectionIdEntry = contextFiltersMap.get(key);

        if (collectionIdEntry == null || (Context.now() - collectionIdEntry.getSecond() > 5 * 60)) {
            Map<String, FilterConfig> configs = FilterYamlTemplateDao.instance.fetchFilterConfig(true, false);
            Map<String, List<String>> templatesByCategory = new HashMap<>();
            for(String templateId : configs.keySet()) {
                if (templateId != null && !templateId.isEmpty()) {
                    FilterConfig config = configs.get(templateId);
                    if(config != null && config.getInfo() != null && config.getInfo().getCategory().getName() != null) {
                        String name = config.getInfo().getCategory().getName();
                        if(name.toLowerCase().contains("mcp")) {
                            List<String> list =  templatesByCategory.getOrDefault(CONTEXT_SOURCE.MCP.name(), new ArrayList<>());
                            list.add(templateId);
                            templatesByCategory.put(CONTEXT_SOURCE.MCP.name(), list);
                        } else if(name.toLowerCase().contains("gen")) {
                            List<String> list =  templatesByCategory.getOrDefault(CONTEXT_SOURCE.GEN_AI.name(), new ArrayList<>());
                            list.add(templateId);
                            templatesByCategory.put(CONTEXT_SOURCE.GEN_AI.name(), list);
                        } else {
                            List<String> list =  templatesByCategory.getOrDefault(CONTEXT_SOURCE.API.name(), new ArrayList<>());
                            list.add(templateId);
                            templatesByCategory.put(CONTEXT_SOURCE.API.name(), list);
                        }
                    }
                }
            }

            List<String> finalIds;
            
            // Handle AGENTIC context - combine both MCP and GenAI templates
            if (source == CONTEXT_SOURCE.AGENTIC) {
                finalIds = new ArrayList<>();
                finalIds.addAll(templatesByCategory.getOrDefault(CONTEXT_SOURCE.MCP.name(), new ArrayList<>()));
                finalIds.addAll(templatesByCategory.getOrDefault(CONTEXT_SOURCE.GEN_AI.name(), new ArrayList<>()));
            } else {
                finalIds = templatesByCategory.getOrDefault(source.name(), new ArrayList<>());
            }

            Set<String> resultSet = finalIds.stream().collect(Collectors.toSet());
            
            // Cache the result for future use
            contextFiltersMap.put(key, new Pair<>(resultSet, Context.now()));
            
            return resultSet;
        } else {
            return collectionIdEntry.getFirst();
        }
    }

    public static void deleteContextCollectionsForUser(int accountId, CONTEXT_SOURCE source) {
        if(source == null) {
            source = CONTEXT_SOURCE.API;
        }
        if(contextFiltersMap.isEmpty()) {
            return;
        }
        Pair<Integer, CONTEXT_SOURCE> key = new Pair<>(accountId, source);
        contextFiltersMap.remove(key);
    }

    @Override
    public String getCollName() {
        return "filter_yaml_templates";
    }

    @Override
    public Class<YamlTemplate> getClassT() {
        return YamlTemplate.class;
    }
}
