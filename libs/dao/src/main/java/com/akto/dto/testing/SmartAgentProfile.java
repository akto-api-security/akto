package com.akto.dto.testing;

import java.util.ArrayList;
import java.util.List;

import org.bson.codecs.pojo.annotations.BsonIgnore;
import org.bson.types.ObjectId;

/**
 * A smart agent: smart testing with its own skills and knowledge base. It is chosen in the Run test modal when smart
 * testing is on; the run gets a copy of it (TestingRun.smartAgentConfig).
 */
public class SmartAgentProfile {

    public static final String ID = "_id";
    private ObjectId id;
    @BsonIgnore
    private String hexId;

    public static final String NAME = "name";
    private String name;
    private String description;

    private List<String> skills = new ArrayList<>();
    private List<SmartAgentKnowledgeFile> customSkills = new ArrayList<>();
    private boolean allowCreateIdentities;
    private String instructions;
    private List<SmartAgentKnowledgeFile> knowledgeFiles = new ArrayList<>();
    private List<String> knowledgeLinks = new ArrayList<>();

    private String createdBy;
    private int createdTs;
    public static final String LAST_UPDATED_BY = "lastUpdatedBy";
    private String lastUpdatedBy;
    public static final String LAST_UPDATED_TS = "lastUpdatedTs";
    private int lastUpdatedTs;

    public SmartAgentProfile() { }

    /** What a test run gets: a copy, so later edits never change that run. */
    public SmartAgentConfig toRunConfig() {
        SmartAgentConfig config = new SmartAgentConfig();
        config.setProfileId(id != null ? id.toHexString() : null);
        config.setProfileName(name);
        config.setSkills(new ArrayList<>(orEmpty(skills)));
        config.setCustomSkills(copy(customSkills));
        config.setAllowCreateIdentities(allowCreateIdentities);
        config.setInstructions(instructions);
        config.setKnowledgeFiles(copy(knowledgeFiles));
        config.setKnowledgeLinks(new ArrayList<>(orEmpty(knowledgeLinks)));
        return config;
    }

    private static List<SmartAgentKnowledgeFile> copy(List<SmartAgentKnowledgeFile> items) {
        List<SmartAgentKnowledgeFile> out = new ArrayList<>();
        for (SmartAgentKnowledgeFile item : orEmpty(items)) {
            out.add(new SmartAgentKnowledgeFile(item.getName(), item.getContent(), item.getSha256(), item.getSize()));
        }
        return out;
    }

    private static <T> List<T> orEmpty(List<T> list) {
        return list != null ? list : new ArrayList<>();
    }

    public ObjectId getId() { return id; }
    public void setId(ObjectId id) { this.id = id; }

    public String getHexId() { return id != null ? id.toHexString() : hexId; }
    public void setHexId(String hexId) { this.hexId = hexId; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

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

    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }

    public int getCreatedTs() { return createdTs; }
    public void setCreatedTs(int createdTs) { this.createdTs = createdTs; }

    public String getLastUpdatedBy() { return lastUpdatedBy; }
    public void setLastUpdatedBy(String lastUpdatedBy) { this.lastUpdatedBy = lastUpdatedBy; }

    public int getLastUpdatedTs() { return lastUpdatedTs; }
    public void setLastUpdatedTs(int lastUpdatedTs) { this.lastUpdatedTs = lastUpdatedTs; }
}
