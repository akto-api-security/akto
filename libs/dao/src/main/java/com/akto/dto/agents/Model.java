package com.akto.dto.agents;

import java.util.Map;

public class Model {

    String name;
    public final static String _NAME = "name";
    ModelType type;

    /*
     * Params contain details like
     * modelName: "gpt-3.5-turbo", ...
     * apiKey: "sk-...",
     * In case of self hosted Azure
     * azureOpenAIEndpoint: "https://<your-resource-name>.openai.azure.com/",
     */

    public final static String PARAM_MODEL = "model";
    public final static String PARAM_API_KEY = "apiKey";
    public final static String PARAM_AZURE_OPENAI_ENDPOINT = "azureOpenAIEndpoint";
    public final static String PARAM_OLLAMA_ENDPOINT = "ollamaAIEndpoint";
    public final static String PARAM_DATABRICKS_ENDPOINT = "databricksEndpoint";
    public final static String PARAM_GITHUB_TOKEN = "githubToken";

    public final static String _PARAMS = "params";
    Map<String, String> params;

    public Model(String name, ModelType type, Map<String, String> params) {
        this.name = name;
        this.type = type;
        this.params = params;
    }

    public Model() {
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Map<String, String> getParams() {
        return params;
    }

    public void setParams(Map<String, String> params) {
        this.params = params;
    }

    public ModelType getType() {
        return type;
    }

    public void setType(ModelType type) {
        this.type = type;
    }

    public String getModelName() {
        return params != null ? params.get(PARAM_MODEL) : null;
    }

    public String getApiKey() {
        return params != null ? params.get(PARAM_API_KEY) : null;
    }

    public String getAzureEndpoint() {
        return params != null ? params.get(PARAM_AZURE_OPENAI_ENDPOINT) : null;
    }

    public String getOllamaEndpoint() {
        return params != null ? params.get(PARAM_OLLAMA_ENDPOINT) : null;
    }

    public String getDatabricksEndpoint() {
        return params != null ? params.get(PARAM_DATABRICKS_ENDPOINT) : null;
    }

}
