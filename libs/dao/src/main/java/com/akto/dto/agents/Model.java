package com.akto.dto.agents;

import java.util.Map;

import org.bson.codecs.pojo.annotations.BsonId;
import org.bson.codecs.pojo.annotations.BsonIgnore;
import org.bson.codecs.pojo.annotations.BsonProperty;
import org.bson.types.ObjectId;

public class Model {

    public static final String _ID = "_id";
    public static final String _NAME = "name";
    public static final String _TYPE = "type";
    public static final String _API_KEY = "apiKey";
    public static final String _MODEL_NAME = "modelName";
    public static final String _ENDPOINT = "endpoint";
    public static final String _DESCRIPTION = "description";
    public static final String _ENABLED = "enabled";
    public static final String _DEFAULT = "default";
    public static final String _CREATED_AT = "createdAt";
    public static final String _UPDATED_AT = "updatedAt";
    public static final String _PARAMS = "params";

    public static final String PARAM_MODEL = "model";
    public static final String PARAM_API_KEY = "apiKey";
    public static final String PARAM_AZURE_OPENAI_ENDPOINT = "azureOpenAIEndpoint";

    @BsonId
    private ObjectId id;
    private String name;
    private ModelType type;
    private String apiKey;
    private String modelName;
    private String endpoint;
    private String description;
    private boolean enabled = true;
    @BsonProperty("default")
    private boolean defaultModel;
    private int createdAt;
    private int updatedAt;
    private Map<String, String> params;

    public Model() {
    }

    public Model(String name, ModelType type, Map<String, String> params) {
        this.name = name;
        this.type = type;
        this.params = params;
    }

    @BsonIgnore
    public String getIdHex() {
        return id != null ? id.toHexString() : null;
    }

    public ObjectId getId() {
        return id;
    }

    public void setId(ObjectId id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public ModelType getType() {
        return type;
    }

    public void setType(ModelType type) {
        this.type = type;
    }

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public String getModelName() {
        return modelName;
    }

    public void setModelName(String modelName) {
        this.modelName = modelName;
    }

    public String getEndpoint() {
        return endpoint;
    }

    public void setEndpoint(String endpoint) {
        this.endpoint = endpoint;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isDefaultModel() {
        return defaultModel;
    }

    public void setDefaultModel(boolean defaultModel) {
        this.defaultModel = defaultModel;
    }

    public int getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(int createdAt) {
        this.createdAt = createdAt;
    }

    public int getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(int updatedAt) {
        this.updatedAt = updatedAt;
    }

    public Map<String, String> getParams() {
        return params;
    }

    public void setParams(Map<String, String> params) {
        this.params = params;
    }
}
