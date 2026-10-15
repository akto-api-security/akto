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
    public final static String PARAM_API_KEY = "apiKey";
    // Env-configured Azure deployment tier (see AzureOpenAIPromptHandler.ModelTier), used by
    // Akto's own service chains instead of an explicit endpoint/key.
    public final static String PARAM_TIER = "tier";
    public final static String PARAM_AZURE_OPENAI_ENDPOINT = "azureOpenAIEndpoint";

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

}
