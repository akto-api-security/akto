package com.akto.dao;

import com.akto.dao.context.Context;
import com.akto.dto.McpGuardrailPolicy;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Updates;
import org.bson.conversions.Bson;

public class McpGuardrailPolicyDao extends AccountsContextDao<McpGuardrailPolicy> {
    public static final McpGuardrailPolicyDao instance = new McpGuardrailPolicyDao();

    @Override
    public String getCollName() {
        return "mcp_guardrail_policies";
    }

    @Override
    public Class<McpGuardrailPolicy> getClassT() {
        return McpGuardrailPolicy.class;
    }

    /**
     * Get the single guardrail policy record
     */
    public McpGuardrailPolicy getPolicy() {
        // Since there's only one policy, get the first (and only) record
        return findOne(Filters.empty());
    }

    /**
     * Create the single default policy if none exists
     */
    public McpGuardrailPolicy createDefaultPolicy() {
        McpGuardrailPolicy defaultPolicy = new McpGuardrailPolicy(
            "system@akto.io", // Default system user
            "MCP Guardrail Policy",
            "Single policy containing all guardrail configurations"
        );
        
        insertOne(defaultPolicy);
        return defaultPolicy;
    }

    /**
     * Get or create the single policy record
     */
    public McpGuardrailPolicy getOrCreatePolicy() {
        McpGuardrailPolicy policy = getPolicy();
        if (policy == null) {
            policy = createDefaultPolicy();
        }
        return policy;
    }

    /**
     * Toggle a specific guardrail on/off
     */
    public void toggleGuardrail(String guardrailType, boolean enabled) {
        Bson filter = Filters.exists("_id"); // Match any record (since there's only one)
        
        Bson update = Updates.combine(
            Updates.set("guardrailConfigs." + guardrailType + ".enabled", enabled),
            Updates.set(McpGuardrailPolicy.UPDATED_AT, Context.now())
        );
        
        updateOneNoUpsert(filter, update);
    }

    /**
     * Update guardrail action
     */
    public void updateGuardrailAction(String guardrailType, String action) {
        Bson filter = Filters.exists("_id"); // Match any record (since there's only one)
        
        Bson update = Updates.combine(
            Updates.set("guardrailConfigs." + guardrailType + ".action", action),
            Updates.set(McpGuardrailPolicy.UPDATED_AT, Context.now())
        );
        
        getMCollection().updateOne(filter, update);
    }

    /**
     * Toggle entire policy on/off
     */
    public void togglePolicyStatus(boolean active) {
        Bson filter = Filters.exists("_id"); // Match any record (since there's only one)
        
        Bson update = Updates.combine(
            Updates.set(McpGuardrailPolicy.ACTIVE, active),
            Updates.set(McpGuardrailPolicy.UPDATED_AT, Context.now())
        );
        
        getMCollection().updateOne(filter, update);
    }
}
