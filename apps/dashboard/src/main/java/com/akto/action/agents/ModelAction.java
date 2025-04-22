package com.akto.action.agents;

import java.util.HashMap;
import java.util.Map;

import com.akto.action.UserAction;
import com.akto.dao.agents.AgentModelDao;
import com.akto.dto.agents.Model;
import com.akto.dto.agents.ModelType;
import com.opensymphony.xwork2.Action;

public class ModelAction extends UserAction {

    String name;
    String model;
    String apiKey;
    String azureOpenAIEndpoint;
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

        if (apiKey == null || apiKey.isEmpty()) {
            addActionError("Please add a apiKey");
            return Action.ERROR.toUpperCase();
        }

        if (type == ModelType.AZURE_OPENAI && (azureOpenAIEndpoint == null
                || azureOpenAIEndpoint.isEmpty())) {
            addActionError("Please add azureOpenAIEndpoint");
            return Action.ERROR.toUpperCase();
        }

        Map<String, String> params = new HashMap<>();
        params.put(Model.PARAM_MODEL, this.model);
        params.put(Model.PARAM_API_KEY, this.apiKey);
        if (type == ModelType.AZURE_OPENAI) {
            params.put(Model.PARAM_AZURE_OPENAI_ENDPOINT, this.azureOpenAIEndpoint);
        }

        Model model = new Model(name, type, params);

        AgentModelDao.instance.insertOne(model);

        return Action.SUCCESS.toUpperCase();
    }

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

    public ModelType getType() {
        return type;
    }

    public void setType(ModelType type) {
        this.type = type;
    }

}
