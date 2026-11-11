package com.akto.dto.testing;

import java.util.ArrayList;
import java.util.List;

/**
 * The smart agent a test run uses, copied onto the TestingRun when the run starts, so editing the agent later never
 * changes that run. Mini-testing sends it with every smart test. A run without it uses smart testing unchanged.
 */
public class SmartAgentConfig {

    public static final int MAX_LIST_ITEMS = 20;
    public static final int MAX_KNOWLEDGE_FILES = 10;
    public static final int MAX_KNOWLEDGE_FILE_CHARS = 50_000;
    public static final int MAX_CUSTOM_SKILL_CHARS = 5_000;
    public static final int MAX_INSTRUCTIONS_CHARS = 2_000;

    private String profileId;
    private String profileName;
    // ids of the built-in skills turned on; test categories come from Run test
    private List<String> skills = new ArrayList<>();
    // the user's own skills: a name and what to do or look for
    private List<SmartAgentKnowledgeFile> customSkills = new ArrayList<>();
    // smart testing may create its own users / keys at other role levels when it needs them
    private boolean allowCreateIdentities;
    // knowledge base
    private String instructions;
    private List<SmartAgentKnowledgeFile> knowledgeFiles = new ArrayList<>();
    private List<String> knowledgeLinks = new ArrayList<>(); // docs (an OpenAPI spec is used as the API's docs) and other links

    public SmartAgentConfig() { }

    public String getProfileId() { return profileId; }
    public void setProfileId(String profileId) { this.profileId = profileId; }

    public String getProfileName() { return profileName; }
    public void setProfileName(String profileName) { this.profileName = profileName; }

    public List<String> getSkills() { return skills; }
    public void setSkills(List<String> skills) { this.skills = skills; }

    public List<SmartAgentKnowledgeFile> getCustomSkills() { return customSkills; }
    public void setCustomSkills(List<SmartAgentKnowledgeFile> customSkills) { this.customSkills = customSkills; }

    public boolean getAllowCreateIdentities() { return allowCreateIdentities; }
    public void setAllowCreateIdentities(boolean allowCreateIdentities) { this.allowCreateIdentities = allowCreateIdentities; }

    public String getInstructions() { return instructions; }
    public void setInstructions(String instructions) { this.instructions = instructions; }

    public List<SmartAgentKnowledgeFile> getKnowledgeFiles() { return knowledgeFiles; }
    public void setKnowledgeFiles(List<SmartAgentKnowledgeFile> knowledgeFiles) { this.knowledgeFiles = knowledgeFiles; }

    public List<String> getKnowledgeLinks() { return knowledgeLinks; }
    public void setKnowledgeLinks(List<String> knowledgeLinks) { this.knowledgeLinks = knowledgeLinks; }
}
