package com.akto.dto.deployment;

import org.bson.codecs.pojo.annotations.BsonId;
import com.akto.dao.context.Context;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
public class DeploymentConfig {

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

    public DeploymentConfig(String id, String name, String type, List<EnvVariable> envVars) {
        this.id = id;
        this.name = name;
        this.type = type;
        this.envVars = envVars;
        this.createdTs = Context.now();
        this.lastUpdatedTs = Context.now();
    }
}