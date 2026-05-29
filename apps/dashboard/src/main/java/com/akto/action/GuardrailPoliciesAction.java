package com.akto.action;

import com.akto.dao.GuardrailPoliciesDao;
import com.akto.dao.context.Context;
import com.akto.dto.GuardrailPolicies;
import com.akto.dto.User;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.akto.util.Constants;
import com.akto.util.enums.GlobalEnums.CONTEXT_SOURCE;
import com.akto.util.enums.GlobalEnums.GuardrailSource;
import com.akto.util.http_util.CoreHTTPClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mongodb.BasicDBObject;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.UpdateOptions;
import com.mongodb.client.model.Updates;

import lombok.Getter;
import lombok.Setter;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.apache.commons.lang3.StringUtils;
import org.bson.conversions.Bson;
import org.bson.types.ObjectId;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;


public class GuardrailPoliciesAction extends UserAction {
    private static final LoggerMaker loggerMaker = new LoggerMaker(GuardrailPoliciesAction.class, LogDb.DASHBOARD);
    private static final ObjectMapper objectMapper = new ObjectMapper();
    private static final OkHttpClient httpClient = CoreHTTPClient.client.newBuilder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(10, TimeUnit.SECONDS)
            .build();


    @Getter
    @Setter
    GuardrailPolicies policy;
    // For updating existing policies
    @Setter
    private String hexId;

    @Getter
    private List<GuardrailPolicies> guardrailPolicies;

    @Getter
    private long total;

    @Setter
    private List<String> policyIds;

    // For playground testing
    @Setter
    private String testInput;
    @Getter
    private BasicDBObject playgroundResult;


    public String fetchGuardrailPolicies() {
        try {
            this.guardrailPolicies  = GuardrailPoliciesDao.instance.findAllSortedByCreatedTimestamp(0, 20);
            this.total = GuardrailPoliciesDao.instance.getTotalCount();
            
            loggerMaker.info("Fetched " + guardrailPolicies.size() + " guardrail policies out of " + total + " total");

            return SUCCESS.toUpperCase();
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb("Error fetching guardrail policies: " + e.getMessage(), LogDb.DASHBOARD);
            return ERROR.toUpperCase();
        }
    }


    public String createGuardrailPolicy() {
        try {
            User user = getSUser();
            int currentTime = Context.now();

            boolean isGithubWorkflow = policy.getSource() == GuardrailSource.GITHUB_WORKFLOW
                    && StringUtils.isNotBlank(policy.getSourceHash());

            CONTEXT_SOURCE contextSource = Context.contextSource.get();
            if (contextSource == null) {
                contextSource = CONTEXT_SOURCE.AGENTIC;
            }
            String createdByValue = user.getLogin();

            if (isGithubWorkflow) {
                GuardrailPolicies existing = GuardrailPoliciesDao.instance.findOne(
                    Filters.and(
                        Filters.eq("name", policy.getName()),
                        Filters.eq("contextSource", contextSource)
                    )
                );
                if (existing != null && policy.getSourceHash().equals(existing.getSourceHash())) {
                    loggerMaker.infoAndAddToDb("GITHUB_WORKFLOW: sourceHash unchanged, skipping update for: " + policy.getName());
                    return SUCCESS.toUpperCase();
                }
                if (existing != null) {
                    hexId = existing.getId().toHexString();
                    loggerMaker.infoAndAddToDb("GITHUB_WORKFLOW: sourceHash changed, updating policy: " + policy.getName());
                } else {
                    hexId = null;
                    loggerMaker.infoAndAddToDb("GITHUB_WORKFLOW: no existing policy, inserting: " + policy.getName());
                }
                createdByValue = GuardrailSource.GITHUB_WORKFLOW.getDisplayName();
            }

            loggerMaker.info("createGuardrailPolicy called with hexId: " + hexId);
            loggerMaker.info("Policy object received: " + (policy != null ? policy.getName() : "null"));
            loggerMaker.info("Context source for guardrail: " + contextSource);

            // Ensure policy object has required timestamps and user info
            if (hexId != null && !hexId.isEmpty()) {
                // Update existing - keep original createdTimestamp, update updatedTimestamp and updatedBy
                if (policy.getUpdatedTimestamp() == 0) {
                    policy.setUpdatedTimestamp(currentTime);
                }
                policy.setUpdatedBy(user.getLogin()); // Always update who modified it
            } else {
                // Create new - only set creation fields, not update fields
                if (policy.getCreatedTimestamp() == 0) {
                    policy.setCreatedTimestamp(currentTime);
                }
                if (policy.getCreatedBy() == null || policy.getCreatedBy().isEmpty()) {
                    policy.setCreatedBy(user.getLogin());
                }
                // Don't set updatedTimestamp and updatedBy for new records
            }

            // Use upsert operation
            Bson filter = (hexId != null && !hexId.isEmpty()) 
                ? Filters.eq(Constants.ID, new ObjectId(hexId))
                : Filters.eq("name", policy.getName()); // or use another unique identifier
            
            List<Bson> updates = buildPolicyUpdates(policy, contextSource);

            // Only set createdBy and createdTimestamp on insert
            updates.add(Updates.setOnInsert("createdBy", createdByValue));
            updates.add(Updates.setOnInsert("createdTimestamp", currentTime));
            updates.add(Updates.set("updatedTimestamp", currentTime));

            // Only set updatedTimestamp and updatedBy on actual updates (when hexId exists)
            if (hexId != null && !hexId.isEmpty()) {
                String updatedByValue = user.getLogin();
                if (isGithubWorkflow) {
                    updatedByValue = GuardrailSource.GITHUB_WORKFLOW.getDisplayName();
                }
                updates.add(Updates.set("updatedBy", updatedByValue));
            }

            // Perform upsert using updateOne with upsert option
            GuardrailPoliciesDao.instance.getMCollection().updateOne(
                filter,
                Updates.combine(updates),
                new UpdateOptions().upsert(true)
            );
            
            String action = (hexId != null && !hexId.isEmpty()) ? "Updated" : "Created";
            loggerMaker.info(action + " guardrail policy: " + policy.getName() + " by user: " + user.getLogin());

            return SUCCESS.toUpperCase();
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb("Error creating guardrail policy: " + e.getMessage(), LogDb.DASHBOARD);
            return ERROR.toUpperCase();
        }
    }

    private List<Bson> buildPolicyUpdates(GuardrailPolicies p, CONTEXT_SOURCE contextSource) {
        List<Bson> updates = new ArrayList<>();

        updates.add(Updates.set("name", p.getName()));
        updates.add(Updates.set("contextSource", contextSource));
        updates.add(Updates.set("applyOnResponse", p.isApplyOnResponse()));
        updates.add(Updates.set("applyOnRequest", p.isApplyOnRequest()));
        updates.add(Updates.set("applyToAllServers", p.isApplyToAllServers()));
        updates.add(Updates.set("active", p.isActive()));

        if (StringUtils.isNotBlank(p.getDescription())) {
            updates.add(Updates.set("description", p.getDescription()));
        }
        if (StringUtils.isNotBlank(p.getBlockedMessage())) {
            updates.add(Updates.set("blockedMessage", p.getBlockedMessage()));
        }
        if (StringUtils.isNotBlank(p.getSeverity())) {
            updates.add(Updates.set("severity", p.getSeverity()));
        }
        if (StringUtils.isNotBlank(p.getSelectedCollection())) {
            updates.add(Updates.set("selectedCollection", p.getSelectedCollection()));
        }
        if (StringUtils.isNotBlank(p.getSelectedModel())) {
            updates.add(Updates.set("selectedModel", p.getSelectedModel()));
        }
        if (p.getDeniedTopics() != null) {
            updates.add(Updates.set("deniedTopics", p.getDeniedTopics()));
        }
        if (p.getPiiTypes() != null) {
            updates.add(Updates.set("piiTypes", p.getPiiTypes()));
        }
        if (p.getRegexPatterns() != null) {
            updates.add(Updates.set("regexPatterns", p.getRegexPatterns()));
        }
        if (p.getRegexPatternsV2() != null) {
            updates.add(Updates.set("regexPatternsV2", p.getRegexPatternsV2()));
        }
        if (p.getContentFiltering() != null) {
            updates.add(Updates.set("contentFiltering", p.getContentFiltering()));
        }
        if (p.getLlmRule() != null) {
            updates.add(Updates.set("llmRule", p.getLlmRule()));
        }
        if (p.getBasePromptRule() != null) {
            updates.add(Updates.set("basePromptRule", p.getBasePromptRule()));
        }
        if (p.getGibberishDetection() != null) {
            updates.add(Updates.set("gibberishDetection", p.getGibberishDetection()));
        }
        if (p.getAnonymizeDetection() != null) {
            updates.add(Updates.set("anonymizeDetection", p.getAnonymizeDetection()));
        }
        if (p.getBanCodeDetection() != null) {
            updates.add(Updates.set("banCodeDetection", p.getBanCodeDetection()));
        }
        if (p.getSecretsDetection() != null) {
            updates.add(Updates.set("secretsDetection", p.getSecretsDetection()));
        }
        if (p.getSentimentDetection() != null) {
            updates.add(Updates.set("sentimentDetection", p.getSentimentDetection()));
        }
        if (p.getTokenLimitDetection() != null) {
            updates.add(Updates.set("tokenLimitDetection", p.getTokenLimitDetection()));
        }
        if (p.getSelectedMcpServers() != null) {
            updates.add(Updates.set("selectedMcpServers", p.getSelectedMcpServers()));
        }
        if (p.getSelectedAgentServers() != null) {
            updates.add(Updates.set("selectedAgentServers", p.getSelectedAgentServers()));
        }
        if (p.getSelectedMcpServersV2() != null) {
            updates.add(Updates.set("selectedMcpServersV2", p.getSelectedMcpServersV2()));
        }
        if (p.getSelectedAgentServersV2() != null) {
            updates.add(Updates.set("selectedAgentServersV2", p.getSelectedAgentServersV2()));
        }
        if (StringUtils.isNotBlank(p.getBehaviour())) {
            updates.add(Updates.set("behaviour", p.getBehaviour()));
        }
        if (StringUtils.isNotBlank(p.getUrl())) {
            updates.add(Updates.set("url", p.getUrl()));
        }
        if (p.getConfidenceScore() > 0) {
            updates.add(Updates.set("confidenceScore", p.getConfidenceScore()));
        }
        if (p.getSource() != null) {
            updates.add(Updates.set("source", p.getSource()));
        }
        if (StringUtils.isNotBlank(p.getSourceHash())) {
            updates.add(Updates.set("sourceHash", p.getSourceHash()));
        }

        return updates;
    }

    public String deleteGuardrailPolicies() {
        try {
            if (policyIds == null || policyIds.isEmpty()) {
                loggerMaker.errorAndAddToDb("No policy IDs provided for deletion", LogDb.DASHBOARD);
                return ERROR.toUpperCase();
            }

            User user = getSUser();
            List<ObjectId> objectIds = new ArrayList<>();
            for (String id : policyIds) {
                objectIds.add(new ObjectId(id));
            }

            Bson filter = Filters.in(GuardrailPoliciesDao.ID, objectIds);
            GuardrailPoliciesDao.instance.getMCollection().deleteMany(filter);

            loggerMaker.info("Deleted " + policyIds.size() + " guardrail policies by user: " + user.getLogin());

            return SUCCESS.toUpperCase();
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb("Error deleting guardrail policies: " + e.getMessage(), LogDb.DASHBOARD);
            return ERROR.toUpperCase();
        }
    }

    public String guardrailPlayground() {
        try {
            if (testInput == null || testInput.trim().isEmpty()) {
                loggerMaker.errorAndAddToDb("Test input is required for playground testing", LogDb.DASHBOARD);
                return ERROR.toUpperCase();
            }

            User user = getSUser();
            int currentTime = Context.now();

            // Build per-account guardrail service URL.
            // Default: https://<accountId>-guardrails.akto.io
            // Exception: the US / demo account (1768362636) is hosted on a custom
            // domain (https://ingest-demo.akto.io) instead of the standard
            // <accountId>-guardrails.akto.io pattern, so it needs a hardcoded override.
            int accountId = Context.accountId.get();
            String guardrailServiceUrl = accountId == 1768362636
                    ? "https://ingest-demo.akto.io"
                    : "https://" + accountId + "-guardrails.akto.io";
            String validateUrl = guardrailServiceUrl + "/api/validate/requestWithPolicy";

            // Prepare request payload - wrap testInput in JSON with "prompt" key
            BasicDBObject promptObject = new BasicDBObject();
            promptObject.put("prompt", testInput);
            String payloadJson = promptObject.toJson();
            
            BasicDBObject requestPayload = new BasicDBObject();
            requestPayload.put("payload", payloadJson);
            
            // Get context source for the request
            CONTEXT_SOURCE contextSource = Context.contextSource.get();
            if (contextSource == null) {
                contextSource = CONTEXT_SOURCE.AGENTIC;
            }
            requestPayload.put("contextSource", contextSource.name());
            
            // For playground testing, skip threat reporting to TBS
            // This allows testing without creating threat events in the dashboard
            requestPayload.put("skipThreat", true);

            // Policy must be provided directly in the request
            if (policy == null) {
                loggerMaker.errorAndAddToDb("No policy provided for playground testing", LogDb.DASHBOARD);
                return ERROR.toUpperCase();
            }
            
            GuardrailPolicies policyToSend = policy;
            loggerMaker.info("Using provided policy for playground testing: " + policy.getName());

            // Ensure policy is active for playground testing
            policyToSend.setActive(true);
            
            // Set context source if not already set
            if (policyToSend.getContextSource() == null) {
                policyToSend.setContextSource(contextSource);
            }
            
            // Ensure applyOnRequest is set (required for request validation)
            if (!policyToSend.isApplyOnRequest() && !policyToSend.isApplyOnResponse()) {
                policyToSend.setApplyOnRequest(true);
            }

            // Serialize policy to JSON and add to request
            try {
                BasicDBObject policyObject = serializePolicyToJson(policyToSend, user, currentTime);
                requestPayload.put("policy", policyObject);
                loggerMaker.info("Playground test with policy: " + policyObject.getString("name"));
                
            } catch (Exception e) {
                loggerMaker.errorAndAddToDb("Failed to serialize policy for playground request: " + e.getMessage(), LogDb.DASHBOARD);
                loggerMaker.errorAndAddToDb(e.toString(), LogDb.DASHBOARD);
                return ERROR.toUpperCase();
            }

            // Call guardrail service using shared HTTP client
            MediaType mediaType = MediaType.parse("application/json");
            RequestBody body = RequestBody.create(requestPayload.toJson(), mediaType);
            Request request = new Request.Builder()
                    .url(validateUrl)
                    .method("POST", body)
                    .addHeader("Content-Type", "application/json")
                    .build();

            // Call guardrail service using shared HTTP client
            try (Response response = httpClient.newCall(request).execute()) {
                ResponseBody responseBodyObj = response.body();
                String responseBody = (responseBodyObj != null) ? responseBodyObj.string() : "";

                if (response.isSuccessful()) {
                    if (responseBody != null && !responseBody.isEmpty()) {
                        try {
                            // Parse the response
                            this.playgroundResult = BasicDBObject.parse(responseBody);
                            loggerMaker.info("Guardrail playground test completed successfully for policy: " + policy.getName());
                            return SUCCESS.toUpperCase();
                        } catch (Exception parseException) {
                            loggerMaker.errorAndAddToDb("Failed to parse guardrail service response: " + parseException.getMessage() + ". Response body: " + responseBody, LogDb.DASHBOARD);
                            return ERROR.toUpperCase();
                        }
                    } else {
                        loggerMaker.errorAndAddToDb("Guardrail service returned empty response body. Status code: " + response.code(), LogDb.DASHBOARD);
                        return ERROR.toUpperCase();
                    }
                } else {
                    String errorMessage = String.format("Guardrail service returned error. Status: %d, Response: %s", response.code(), responseBody);
                    loggerMaker.errorAndAddToDb(errorMessage, LogDb.DASHBOARD);
                    return ERROR.toUpperCase();
                }
            } catch (IOException e) {
                loggerMaker.errorAndAddToDb("IO error calling guardrail service at " + validateUrl + ": " + e.getMessage(), LogDb.DASHBOARD);
                loggerMaker.errorAndAddToDb(e.toString(), LogDb.DASHBOARD);
                return ERROR.toUpperCase();
            }
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb("Error in guardrail playground test: " + e.getMessage(), LogDb.DASHBOARD);
            return ERROR.toUpperCase();
        }
    }

    /**
     * Serializes a GuardrailPolicies object to BasicDBObject for JSON transmission
     * This method handles all policy fields including nested objects
     */
    private BasicDBObject serializePolicyToJson(GuardrailPolicies policy, User user, int currentTime) {
        // Use Jackson to serialize the DTO to a Map (handles all nested objects automatically)
        @SuppressWarnings("unchecked")
        Map<String, Object> policyMap = objectMapper.convertValue(policy, Map.class);
        
        // Remove internal/MongoDB-specific fields that shouldn't be sent to guardrail service
        policyMap.remove("id");
        policyMap.remove("hexId");
        policyMap.remove("createdTimestamp");
        policyMap.remove("updatedTimestamp");
        policyMap.remove("createdBy");
        policyMap.remove("updatedBy");
        
        // Ensure required fields are set
        String policyName = policy.getName();
        if (policyName == null || policyName.isEmpty()) {
            policyName = "PLAYGROUND_TEST_" + user.getLogin() + "_" + currentTime;
        }
        policyMap.put("name", policyName);
        policyMap.put("active", true); // Always active for playground testing
        
        // Serialize CONTEXT_SOURCE enum to string (Jackson handles this automatically, but ensure it's a string)
        if (policy.getContextSource() != null) {
            policyMap.put("contextSource", policy.getContextSource().name());
        }
        
        // Add policy version for future compatibility
        policyMap.put("policyVersion", "1.0");
        
        // Convert Map to BasicDBObject for compatibility with existing code
        return new BasicDBObject(policyMap);
    }

}