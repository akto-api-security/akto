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

}
