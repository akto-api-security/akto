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
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.opensymphony.xwork2.Action;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class ModelAction extends UserAction {

    String name;
    String model;
    String apiKey;
    String azureOpenAIEndpoint;
    String ollamaAIEndpoint;
    String databricksEndpoint;
    String githubToken;
    ModelType type;

    public String saveAgentModel() {
        if (name == null || name.isEmpty()) {
            addActionError("Please add a model name");
            return Action.ERROR.toUpperCase();
        }

        Model existingModel = AgentModelDao.instance.findByName(name);

        if (existingModel != null) {
            addActionError("Existing model with same name");
            return Action.ERROR.toUpperCase();
        }

        if (type == null) {
            addActionError("Please select a model type");
            return Action.ERROR.toUpperCase();
        }

        if (model == null || model.isEmpty()) {
            addActionError("Please add a model");
            return Action.ERROR.toUpperCase();
        }

        /*
         * TODO: validate model name based on type
         * 
         * e.g. openAI model should be like gpt-4o
         * Anthropic model should be like claude-3 etc.
         */

        if ((apiKey == null || apiKey.isEmpty()) && type != ModelType.OLLAMA && type != ModelType.GITHUB_MODELS) {
            addActionError("Please add a apiKey");
            return Action.ERROR.toUpperCase();
        }

        if (type == ModelType.AZURE_OPENAI && (azureOpenAIEndpoint == null
                || azureOpenAIEndpoint.isEmpty())) {
            addActionError("Please add azureOpenAIEndpoint");
            return Action.ERROR.toUpperCase();
        }

        if (type == ModelType.OLLAMA && (ollamaAIEndpoint == null || ollamaAIEndpoint.isEmpty())) {
            addActionError("Please add ollamaAIEndpoint");
            return Action.ERROR.toUpperCase();
        }

        if (type == ModelType.DATABRICKS && (databricksEndpoint == null || databricksEndpoint.isEmpty())) {
            addActionError("Please add Databricks workspace endpoint");
            return Action.ERROR.toUpperCase();
        }

        if (type == ModelType.GITHUB_MODELS && (githubToken == null || githubToken.isEmpty())) {
            addActionError("Please add GitHub Personal Access Token");
            return Action.ERROR.toUpperCase();
        }

        Map<String, String> params = new HashMap<>();
        params.put(Model.PARAM_MODEL, this.model);
        params.put(Model.PARAM_API_KEY, this.apiKey);
        if (type == ModelType.AZURE_OPENAI) {
            params.put(Model.PARAM_AZURE_OPENAI_ENDPOINT, this.azureOpenAIEndpoint);
        }
        if (type == ModelType.OLLAMA) {
            params.put(Model.PARAM_OLLAMA_ENDPOINT, this.ollamaAIEndpoint);
        }
        if (type == ModelType.DATABRICKS) {
            params.put(Model.PARAM_DATABRICKS_ENDPOINT, this.databricksEndpoint);
        }
        if (type == ModelType.GITHUB_MODELS) {
            params.put(Model.PARAM_GITHUB_TOKEN, this.githubToken);
        }

        Model model = new Model(name, type, params);

        AgentModelDao.instance.insertOne(model);

        return Action.SUCCESS.toUpperCase();
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

    public String getApiKey() {
        return apiKey;
    }

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

    public String getGithubToken() {
        return githubToken;
    }

    public void setGithubToken(String githubToken) {
        this.githubToken = githubToken;
    }

    public ModelType getType() {
        return type;
    }

    public void setType(ModelType type) {
        this.type = type;
    }

    List<Map<String, String>> githubModels;

    public String fetchGithubModels() {
        if (githubToken == null || githubToken.isEmpty()) {
            addActionError("Please provide GitHub Personal Access Token");
            return Action.ERROR.toUpperCase();
        }

        try {
            OkHttpClient client = new OkHttpClient();
            Request request = new Request.Builder()
                    .url("https://models.github.ai/v1/models")
                    .header("Accept", "application/vnd.github+json")
                    .header("Authorization", "Bearer " + githubToken)
                    .header("X-GitHub-Api-Version", "2022-11-28")
                    .get()
                    .build();

            try (Response response = client.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    addActionError("Failed to fetch GitHub models: " + response.code());
                    return Action.ERROR.toUpperCase();
                }

                String responseBody = response.body().string();
                ObjectMapper mapper = new ObjectMapper();
                JsonNode root = mapper.readTree(responseBody);
                
                githubModels = new ArrayList<>();
                
                if (root.has("data") && root.get("data").isArray()) {
                    for (JsonNode modelNode : root.get("data")) {
                        if (modelNode.has("id")) {
                            String modelId = modelNode.get("id").asText();
                            String displayName = modelId;
                            
                            // Create friendly display name
                            if (modelNode.has("name")) {
                                displayName = modelNode.get("name").asText();
                            }
                            
                            Map<String, String> modelInfo = new HashMap<>();
                            modelInfo.put("value", modelId);
                            modelInfo.put("label", displayName);
                            githubModels.add(modelInfo);
                        }
                    }
                }

                return Action.SUCCESS.toUpperCase();
            }
        } catch (Exception e) {
            addActionError("Error fetching GitHub models: " + e.getMessage());
            return Action.ERROR.toUpperCase();
        }
    }

    public List<Map<String, String>> getGithubModels() {
        return githubModels;
    }

    public void setGithubModels(List<Map<String, String>> githubModels) {
        this.githubModels = githubModels;
    }

}
