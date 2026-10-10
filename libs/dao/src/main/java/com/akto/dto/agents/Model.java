package com.akto.dto.agents;

import java.util.Map;

import org.bson.codecs.pojo.annotations.BsonIgnore;

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
    // Optional cheaper/faster model for quick sub-tasks (e.g. red teaming's lightweight
    // steps); consumers fall back to PARAM_MODEL when it's absent.
    public final static String PARAM_FAST_MODEL = "fastModel";
    public final static String PARAM_API_KEY = "apiKey";
    public final static String PARAM_AZURE_OPENAI_ENDPOINT = "azureOpenAIEndpoint";
    public final static String PARAM_OLLAMA_ENDPOINT = "ollamaAIEndpoint";
    public final static String PARAM_DATABRICKS_ENDPOINT = "databricksEndpoint";
    public final static String PARAM_GITHUB_TOKEN = "githubToken";
    public final static String PARAM_CLOUDFLARE_ACCOUNT_ID = "cloudflareAccountId";
    // Vertex AI: the service-account JSON is stored as the apiKey (the secret).
    public final static String PARAM_VERTEX_PROJECT_ID = "vertexProjectId";
    public final static String PARAM_VERTEX_LOCATION = "vertexLocation";
    public final static String PARAM_VERTEX_ENDPOINT_ID = "vertexEndpointId";
    public final static String PARAM_VERTEX_ENDPOINT_DOMAIN = "vertexEndpointDomain";
    // AWS Bedrock: the apiKey is an optional Bedrock API key; without one the
    // consumer's own AWS credentials (e.g. a pod IAM role) are used.
    public final static String PARAM_AWS_REGION = "awsRegion";

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

    /*
     * Convenience views over `params`. @BsonIgnore keeps the Mongo POJO codec (automatic
     * mode maps every getter) from writing them as duplicate top-level fields.
     */
    @BsonIgnore
    public String getModelName() {
        return params != null ? params.get(PARAM_MODEL) : null;
    }

    /*
     * Deliberately not a getter: neither the Mongo codec nor Struts' JSON results
     * serialize it, so the key never appears as a field of its own.
     */
    public String readApiKey() {
        return params != null ? params.get(PARAM_API_KEY) : null;
    }

    @BsonIgnore
    public String getAzureEndpoint() {
        return params != null ? params.get(PARAM_AZURE_OPENAI_ENDPOINT) : null;
    }

    @BsonIgnore
    public String getOllamaEndpoint() {
        return params != null ? params.get(PARAM_OLLAMA_ENDPOINT) : null;
    }

    @BsonIgnore
    public String getDatabricksEndpoint() {
        return params != null ? params.get(PARAM_DATABRICKS_ENDPOINT) : null;
    }

}
