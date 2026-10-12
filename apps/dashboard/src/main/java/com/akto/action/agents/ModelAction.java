package com.akto.action.agents;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.akto.action.UserAction;
import com.akto.audit_logs_util.Audit;
import com.akto.dao.agents.AgentModelDao;
import com.akto.dto.agents.Model;
import com.akto.dto.agents.ModelType;
import com.akto.dto.audit_logs.Operation;
import com.akto.dto.audit_logs.Resource;
import com.mongodb.client.model.Filters;
import com.opensymphony.xwork2.Action;
import org.bson.conversions.Bson;

public class ModelAction extends UserAction {

    String name;
    String model;
    String fastModel;
    String apiKey;
    String azureOpenAIEndpoint;
    String ollamaAIEndpoint;
    String databricksEndpoint;
    String cloudflareAccountId;
    String vertexProjectId;
    String vertexLocation;
    String vertexEndpointId;
    String vertexEndpointDomain;
    String awsRegion;
    ModelType type;

    @Audit(description = "User added an agent model", resource = Resource.AI_AGENTS, operation = Operation.CREATE, metadataGenerators = {"getName"})
    public String saveAgentModel() {
        if (isBlank(name)) {
            addActionError("Please add a model name");
            return Action.ERROR.toUpperCase();
        }

        if (AgentModelDao.instance.findByName(name) != null) {
            addActionError("Existing model with same name");
            return Action.ERROR.toUpperCase();
        }

        String error = validationError();
        if (error != null) {
            addActionError(error);
            return Action.ERROR.toUpperCase();
        }

        AgentModelDao.instance.insertOne(new Model(name, type, buildParams()));
        return Action.SUCCESS.toUpperCase();
    }

    /*
     * Returns an error message, or null when the request is valid.
     *
     * TODO: validate model name based on type
     * e.g. openAI model should be like gpt-4o, Anthropic model should be like claude-3 etc.
     */
    private String validationError() {
        if (type == null) {
            return "Please select a model type";
        }
        // Red teaming has default models for these providers (smart testing still needs one).
        boolean modelOptional = type == ModelType.ANTHROPIC || type == ModelType.BEDROCK || type == ModelType.VERTEX_AI;
        if (isBlank(model) && !modelOptional) {
            return "Please add a model";
        }
        // Ollama can be unauthenticated; Bedrock can use the consumer's own AWS credentials.
        boolean apiKeyOptional = type == ModelType.OLLAMA || type == ModelType.BEDROCK;
        if (isBlank(apiKey) && !apiKeyOptional) {
            return type == ModelType.VERTEX_AI ? "Please add the service account JSON" : "Please add a apiKey";
        }
        if (type == ModelType.AZURE_OPENAI && isBlank(azureOpenAIEndpoint)) {
            return "Please add azureOpenAIEndpoint";
        }
        if (type == ModelType.OLLAMA && isBlank(ollamaAIEndpoint)) {
            return "Please add ollamaAIEndpoint";
        }
        if (type == ModelType.DATABRICKS && isBlank(databricksEndpoint)) {
            return "Please add Databricks workspace endpoint";
        }
        if (type == ModelType.CLOUDFLARE && isBlank(cloudflareAccountId)) {
            return "Please add Cloudflare account ID";
        }
        if (type == ModelType.VERTEX_AI && (isBlank(vertexProjectId) || isBlank(vertexLocation) || isBlank(vertexEndpointId))) {
            return "Please add Vertex AI project ID, location and endpoint ID";
        }
        if (type == ModelType.BEDROCK && isBlank(awsRegion)) {
            return "Please add AWS region";
        }
        return null;
    }

    // Stores only the values provided (trimmed); provider-specific ones only for their type.
    private Map<String, String> buildParams() {
        Map<String, String> params = new HashMap<>();
        putIfPresent(params, Model.PARAM_MODEL, model);
        putIfPresent(params, Model.PARAM_FAST_MODEL, fastModel);
        putIfPresent(params, Model.PARAM_API_KEY, apiKey);
        switch (type) {
            case AZURE_OPENAI:
                putIfPresent(params, Model.PARAM_AZURE_OPENAI_ENDPOINT, azureOpenAIEndpoint);
                break;
            case OLLAMA:
                putIfPresent(params, Model.PARAM_OLLAMA_ENDPOINT, ollamaAIEndpoint);
                break;
            case DATABRICKS:
                putIfPresent(params, Model.PARAM_DATABRICKS_ENDPOINT, databricksEndpoint);
                break;
            case CLOUDFLARE:
                putIfPresent(params, Model.PARAM_CLOUDFLARE_ACCOUNT_ID, cloudflareAccountId);
                break;
            case VERTEX_AI:
                putIfPresent(params, Model.PARAM_VERTEX_PROJECT_ID, vertexProjectId);
                putIfPresent(params, Model.PARAM_VERTEX_LOCATION, vertexLocation);
                putIfPresent(params, Model.PARAM_VERTEX_ENDPOINT_ID, vertexEndpointId);
                putIfPresent(params, Model.PARAM_VERTEX_ENDPOINT_DOMAIN, vertexEndpointDomain);
                break;
            case BEDROCK:
                putIfPresent(params, Model.PARAM_AWS_REGION, awsRegion);
                break;
            default:
                break;
        }
        return params;
    }

    private static void putIfPresent(Map<String, String> params, String key, String value) {
        if (!isBlank(value)) {
            params.put(key, value.trim());
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    @Audit(description = "User deleted an agent model", resource = Resource.AI_AGENTS, operation = Operation.DELETE, metadataGenerators = {"getName"})
    public String deleteAgentModel() {
        if (name == null || name.isEmpty()) {
            addActionError("Please add a model name");
            return Action.ERROR.toUpperCase();
        }

        Model existingModel = AgentModelDao.instance.findByName(name);

        if (existingModel == null) {
            addActionError("No model found");
            return Action.ERROR.toUpperCase();
        }

        AgentModelDao.instance.deleteByName(name);

        return Action.SUCCESS.toUpperCase();
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public String getFastModel() {
        return fastModel;
    }

    public void setFastModel(String fastModel) {
        this.fastModel = fastModel;
    }

    // Write-only: no getter, so the JSON result never echoes the key back.
    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public String getAzureOpenAIEndpoint() {
        return azureOpenAIEndpoint;
    }

    public void setAzureOpenAIEndpoint(String azureOpenAIEndpoint) {
        this.azureOpenAIEndpoint = azureOpenAIEndpoint;
    }

    public String getOllamaAIEndpoint() {
        return ollamaAIEndpoint;
    }

    public void setOllamaAIEndpoint(String ollamaAIEndpoint) {
        this.ollamaAIEndpoint = ollamaAIEndpoint;
    }

    public String getDatabricksEndpoint() {
        return databricksEndpoint;
    }

    public void setDatabricksEndpoint(String databricksEndpoint) {
        this.databricksEndpoint = databricksEndpoint;
    }

    public String getCloudflareAccountId() {
        return cloudflareAccountId;
    }

    public void setCloudflareAccountId(String cloudflareAccountId) {
        this.cloudflareAccountId = cloudflareAccountId;
    }

    public String getVertexProjectId() {
        return vertexProjectId;
    }

    public void setVertexProjectId(String vertexProjectId) {
        this.vertexProjectId = vertexProjectId;
    }

    public String getVertexLocation() {
        return vertexLocation;
    }

    public void setVertexLocation(String vertexLocation) {
        this.vertexLocation = vertexLocation;
    }

    public String getVertexEndpointId() {
        return vertexEndpointId;
    }

    public void setVertexEndpointId(String vertexEndpointId) {
        this.vertexEndpointId = vertexEndpointId;
    }

    public String getVertexEndpointDomain() {
        return vertexEndpointDomain;
    }

    public void setVertexEndpointDomain(String vertexEndpointDomain) {
        this.vertexEndpointDomain = vertexEndpointDomain;
    }

    public String getAwsRegion() {
        return awsRegion;
    }

    public void setAwsRegion(String awsRegion) {
        this.awsRegion = awsRegion;
    }

    public ModelType getType() {
        return type;
    }

    public void setType(ModelType type) {
        this.type = type;
    }

    List<Model> githubCopilotConfigs;

    public String fetchGithubCopilotConfigs() {
        try {
            Bson filter = Filters.eq("type", ModelType.GITHUB_COPILOT);
            githubCopilotConfigs = AgentModelDao.instance.findAll(filter);
            
            if (githubCopilotConfigs == null) {
                githubCopilotConfigs = new ArrayList<>();
            }
            
            return Action.SUCCESS.toUpperCase();
        } catch (Exception e) {
            addActionError("Error fetching GitHub Copilot configurations: " + e.getMessage());
            return Action.ERROR.toUpperCase();
        }
    }

    public List<Model> getGithubCopilotConfigs() {
        return githubCopilotConfigs;
    }

    public void setGithubCopilotConfigs(List<Model> githubCopilotConfigs) {
        this.githubCopilotConfigs = githubCopilotConfigs;
    }


}
