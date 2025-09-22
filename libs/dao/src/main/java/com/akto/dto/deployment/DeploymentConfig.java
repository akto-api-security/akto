package com.akto.dto.deployment;

import org.bson.codecs.pojo.annotations.BsonId;
import com.akto.dao.context.Context;

import java.util.List;

public class DeploymentConfig {

    public static class EnvVariable {
        private String key;
        private String value;
        private boolean editable;

        public EnvVariable() {}

        public EnvVariable(String key, String value, boolean editable) {
            this.key = key;
            this.value = value;
            this.editable = editable;
        }

        public String getKey() {
            return key;
        }

        public void setKey(String key) {
            this.key = key;
        }

        public String getValue() {
            return value;
        }

        public void setValue(String value) {
            this.value = value;
        }

        public boolean isEditable() {
            return editable;
        }

        public void setEditable(boolean editable) {
            this.editable = editable;
        }
    }

    @BsonId
    private String id;
    private String name;
    private String type;
    private List<EnvVariable> envVars;
    private int createdTs;
    private int lastUpdatedTs;

    public static final String ID = "_id";
    public static final String NAME = "name";
    public static final String TYPE = "type";
    public static final String ENV_VARS = "envVars";
    public static final String CREATED_TS = "createdTs";
    public static final String LAST_UPDATED_TS = "lastUpdatedTs";

    public DeploymentConfig() {}

    public DeploymentConfig(String id, String name, String type, List<EnvVariable> envVars) {
        this.id = id;
        this.name = name;
        this.type = type;
        this.envVars = envVars;
        this.createdTs = Context.now();
        this.lastUpdatedTs = Context.now();
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public List<EnvVariable> getEnvVars() {
        return envVars;
    }

    public void setEnvVars(List<EnvVariable> envVars) {
        this.envVars = envVars;
    }

    public int getCreatedTs() {
        return createdTs;
    }

    public void setCreatedTs(int createdTs) {
        this.createdTs = createdTs;
    }

    public int getLastUpdatedTs() {
        return lastUpdatedTs;
    }

    public void setLastUpdatedTs(int lastUpdatedTs) {
        this.lastUpdatedTs = lastUpdatedTs;
    }
}
