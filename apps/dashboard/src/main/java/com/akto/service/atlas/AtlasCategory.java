package com.akto.service.atlas;

import com.akto.util.AgenticObserveUtil;
import com.akto.util.McpClientRegistry;

import java.util.Arrays;
import java.util.List;

/** The overview's tool categories and how a tool row falls into one. */
public final class AtlasCategory {

    private AtlasCategory() {}

    public static final String ALL = "all";
    public static final String AGENTS = AtlasPeopleService.CAT_AGENTS;
    public static final String MCP = AtlasPeopleService.CAT_MCP;
    public static final String BROWSER = AtlasPeopleService.CAT_BROWSER;
    public static final String SKILLS = "skills";
    public static final String IDE = AtlasPeopleService.CAT_IDE;
    public static final List<String> ORDER = Arrays.asList(AGENTS, IDE, MCP, BROWSER, SKILLS);

    public static String of(AtlasToolRow row) {
        String type = row.getRowType();
        if (AtlasAssetStatusService.ROW_TYPE_SKILL.equals(type) || AtlasAssetStatusService.ROW_TYPE_PLUGIN.equals(type)) return SKILLS;
        if (AtlasAssetStatusService.ROW_TYPE_LLM.equals(type)) return BROWSER;
        if (AtlasAssetStatusService.ROW_TYPE_SERVICE.equals(type)) return MCP;
        // The AI inventory's rule: an AI agent on the IDE list is an IDE assistant, any other is an agent or CLI.
        boolean ide = AgenticObserveUtil.CLIENT_TYPE_AI_AGENT.equals(row.getClientType()) && McpClientRegistry.isIdeAssistant(row.getGroupKey());
        return ide ? IDE : AGENTS;
    }
}
