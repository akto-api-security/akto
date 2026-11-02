package com.akto.action.endpoint_shield;

import com.akto.action.UserAction;
import com.akto.dao.context.Context;
import com.akto.dao.endpoint_shield.AgentFileConfigDao;
import com.akto.dao.endpoint_shield.FileInspectionRuleDao;
import com.akto.dto.endpoint_shield.AgentFileConfig;
import com.akto.dto.endpoint_shield.FileInspectionRule;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mongodb.bulk.BulkWriteResult;
import com.mongodb.client.model.BulkWriteOptions;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Sorts;
import com.mongodb.client.model.UpdateOneModel;
import com.mongodb.client.model.UpdateOptions;
import com.mongodb.client.model.Updates;
import com.mongodb.client.model.WriteModel;
import com.opensymphony.xwork2.Action;
import lombok.Getter;
import lombok.Setter;
import org.apache.commons.lang3.StringUtils;
import org.bson.conversions.Bson;
import org.bson.types.ObjectId;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class AgentFileConfigAction extends UserAction {

    private static final LoggerMaker logger = new LoggerMaker(AgentFileConfigAction.class, LogDb.DASHBOARD);
    private static final int MAX_AGENT_LENGTH = 100;
    private static final Set<String> VALID_OS = new HashSet<>(Arrays.asList("darwin", "linux", "windows"));
    private static final Set<String> VALID_ROLES = new HashSet<>(Arrays.asList("login", "credentials", "mcp_config"));
    private static final String DEFAULTS_RESOURCE = "/file_inspection_agent_defaults.json";
    private static final String DEFAULTS_ADDED_BY = "akto-default";
    public static final String RULE_ADDED_BY = "agent-file-config";

    @Setter private String configId;
    @Setter private String agent;
    @Setter private String os;
    @Setter private String role;
    @Setter private String path;

    @Getter private AgentFileConfig config;
    @Getter private List<AgentFileConfig> configs;
    @Getter private int createdCount;
    @Getter private int existingCount;

    public String fetchAgentFileConfig() {
        try {
            AgentFileConfigDao.instance.createIndicesIfAbsent();
            this.configs = AgentFileConfigDao.instance.findAll(Filters.empty(), 0, 5000, Sorts.ascending(AgentFileConfig.AGENT, AgentFileConfig.OS, AgentFileConfig.ROLE, AgentFileConfig.PATH));
            return Action.SUCCESS.toUpperCase();
        } catch (Exception e) {
            logger.errorAndAddToDb(e, "fetchAgentFileConfig failed: " + e.getMessage());
            addActionError("Failed to fetch agent file config");
            return Action.ERROR.toUpperCase();
        }
    }

    public String addAgentFileConfig() {
        try {
            AgentFileConfigDao.instance.createIndicesIfAbsent();
            String validation = validateInput();
            if (validation != null) {
                addActionError(validation);
                return Action.ERROR.toUpperCase();
            }
            if (AgentFileConfigDao.instance.findOne(keyFilter()) != null) {
                addActionError("This file is already configured for the agent");
                return Action.ERROR.toUpperCase();
            }
            int now = Context.now();
            AgentFileConfig c = new AgentFileConfig();
            c.setId(new ObjectId());
            c.setAgent(agent.trim());
            c.setOs(normalize(os));
            c.setRole(normalize(role));
            c.setPath(path.trim());
            c.setAddedBy(getSUser() == null ? null : getSUser().getLogin());
            c.setCreatedTs(now);
            c.setUpdatedTs(now);
            AgentFileConfigDao.instance.insertOne(c);
            syncRules();
            this.config = c;
            return Action.SUCCESS.toUpperCase();
        } catch (Exception e) {
            logger.errorAndAddToDb(e, "addAgentFileConfig failed: " + e.getMessage());
            addActionError("Failed to add agent file");
            return Action.ERROR.toUpperCase();
        }
    }

    public String updateAgentFileConfig() {
        try {
            if (StringUtils.isBlank(configId) || !ObjectId.isValid(configId.trim())) {
                addActionError("Valid configId is required");
                return Action.ERROR.toUpperCase();
            }
            String validation = validateInput();
            if (validation != null) {
                addActionError(validation);
                return Action.ERROR.toUpperCase();
            }
            ObjectId id = new ObjectId(configId.trim());
            Bson filter = Filters.eq(AgentFileConfig.ID, id);
            AgentFileConfig existing = AgentFileConfigDao.instance.findOne(filter);
            if (existing == null) {
                addActionError("Agent file not found");
                return Action.ERROR.toUpperCase();
            }
            AgentFileConfig same = AgentFileConfigDao.instance.findOne(keyFilter());
            if (same != null && !id.equals(same.getId())) {
                addActionError("This file is already configured for the agent");
                return Action.ERROR.toUpperCase();
            }
            existing.setAgent(agent.trim());
            existing.setOs(normalize(os));
            existing.setRole(normalize(role));
            existing.setPath(path.trim());
            existing.setUpdatedTs(Context.now());
            AgentFileConfigDao.instance.replaceOne(filter, existing);
            syncRules();
            this.config = existing;
            return Action.SUCCESS.toUpperCase();
        } catch (Exception e) {
            logger.errorAndAddToDb(e, "updateAgentFileConfig failed: " + e.getMessage());
            addActionError("Failed to update agent file");
            return Action.ERROR.toUpperCase();
        }
    }

    public String deleteAgentFileConfig() {
        try {
            if (StringUtils.isBlank(configId) || !ObjectId.isValid(configId.trim())) {
                addActionError("Valid configId is required");
                return Action.ERROR.toUpperCase();
            }
            AgentFileConfigDao.instance.deleteAll(Filters.eq(AgentFileConfig.ID, new ObjectId(configId.trim())));
            syncRules();
            return Action.SUCCESS.toUpperCase();
        } catch (Exception e) {
            logger.errorAndAddToDb(e, "deleteAgentFileConfig failed: " + e.getMessage());
            addActionError("Failed to delete agent file");
            return Action.ERROR.toUpperCase();
        }
    }

    public String restoreAgentFileConfigDefaults() {
        try {
            AgentFileConfigDao.instance.createIndicesIfAbsent();
            JsonNode defaults;
            try (InputStream in = AgentFileConfigAction.class.getResourceAsStream(DEFAULTS_RESOURCE)) {
                defaults = new ObjectMapper().readTree(in);
            }
            int now = Context.now();
            List<WriteModel<AgentFileConfig>> writes = new ArrayList<>();
            for (JsonNode d : defaults) {
                Bson key = Filters.and(
                    Filters.eq(AgentFileConfig.AGENT, d.path(AgentFileConfig.AGENT).asText()),
                    Filters.eq(AgentFileConfig.OS, d.path(AgentFileConfig.OS).asText()),
                    Filters.eq(AgentFileConfig.ROLE, d.path(AgentFileConfig.ROLE).asText()),
                    Filters.eq(AgentFileConfig.PATH, d.path(AgentFileConfig.PATH).asText())
                );
                Bson insert = Updates.combine(
                    Updates.setOnInsert(AgentFileConfig.ADDED_BY, DEFAULTS_ADDED_BY),
                    Updates.setOnInsert(AgentFileConfig.CREATED_TS, now),
                    Updates.setOnInsert(AgentFileConfig.UPDATED_TS, now)
                );
                writes.add(new UpdateOneModel<>(key, insert, new UpdateOptions().upsert(true)));
            }
            BulkWriteResult result = AgentFileConfigDao.instance.bulkWrite(writes, new BulkWriteOptions().ordered(false));
            createdCount = result.getUpserts().size();
            existingCount = writes.size() - createdCount;
            syncRules();
            return Action.SUCCESS.toUpperCase();
        } catch (Exception e) {
            logger.errorAndAddToDb(e, "restoreAgentFileConfigDefaults failed: " + e.getMessage());
            addActionError("Failed to restore defaults");
            return Action.ERROR.toUpperCase();
        }
    }

    private static void syncRules() {
        FileInspectionRuleDao.instance.createIndicesIfAbsent();
        Set<String> paths = AgentFileConfigDao.instance.findDistinctFields(AgentFileConfig.PATH, String.class, Filters.empty());
        int now = Context.now();
        List<WriteModel<FileInspectionRule>> writes = new ArrayList<>();
        for (String p : paths) {
            Bson insert = Updates.combine(
                Updates.setOnInsert(FileInspectionRule.ACCOUNT_ID, Context.accountId.get()),
                Updates.setOnInsert(FileInspectionRule.EXISTENCE_ONLY, false),
                Updates.setOnInsert("maxDepth", 0),
                Updates.setOnInsert(FileInspectionRule.ADDED_BY, RULE_ADDED_BY),
                Updates.setOnInsert(FileInspectionRule.CREATED_TS, now),
                Updates.setOnInsert(FileInspectionRule.UPDATED_TS, now)
            );
            writes.add(new UpdateOneModel<>(Filters.eq(FileInspectionRule.PATH, p), insert, new UpdateOptions().upsert(true)));
        }
        if (!writes.isEmpty()) {
            FileInspectionRuleDao.instance.bulkWrite(writes, new BulkWriteOptions().ordered(false));
        }
        FileInspectionRuleDao.instance.deleteAll(Filters.and(
            Filters.eq(FileInspectionRule.ADDED_BY, RULE_ADDED_BY),
            Filters.nin(FileInspectionRule.PATH, paths)
        ));
    }

    private Bson keyFilter() {
        return Filters.and(
            Filters.eq(AgentFileConfig.AGENT, agent.trim()),
            Filters.eq(AgentFileConfig.OS, normalize(os)),
            Filters.eq(AgentFileConfig.ROLE, normalize(role)),
            Filters.eq(AgentFileConfig.PATH, path.trim())
        );
    }

    private String validateInput() {
        if (StringUtils.isBlank(agent)) return "Agent is required";
        if (agent.trim().length() > MAX_AGENT_LENGTH) return "Agent name too long (max " + MAX_AGENT_LENGTH + ")";
        if (!VALID_OS.contains(normalize(os))) return "Invalid os: " + os;
        if (!VALID_ROLES.contains(normalize(role))) return "Invalid role: " + role;
        return FileInspectionRuleAction.validatePath(path);
    }

    private static String normalize(String v) {
        String t = StringUtils.trimToNull(v);
        return t == null ? null : t.toLowerCase();
    }
}
