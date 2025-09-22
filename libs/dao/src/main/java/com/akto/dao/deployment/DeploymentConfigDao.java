package com.akto.dao.deployment;

import com.akto.dao.AccountsContextDao;
import com.akto.dto.deployment.DeploymentConfig;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Updates;
import com.mongodb.BasicDBObject;

import java.util.List;

public class DeploymentConfigDao extends AccountsContextDao<DeploymentConfig> {

    @Override
    public String getCollName() {
        return "deployment_configs";
    }

    public static final DeploymentConfigDao instance = new DeploymentConfigDao();
    
    private DeploymentConfigDao() {}

    @Override
    public Class<DeploymentConfig> getClassT() {
        return DeploymentConfig.class;
    }

    public DeploymentConfig findByName(String name) {
        return instance.findOne(Filters.eq(DeploymentConfig.NAME, name));
    }

    public List<DeploymentConfig> findAllDeployments() {
        return instance.findAll(new BasicDBObject());
    }

    public boolean updateEnvVars(String deploymentId, List<DeploymentConfig.EnvVariable> envVars) {
        return instance.updateOne(
            Filters.eq(DeploymentConfig.ID, deploymentId),
            Updates.combine(
                Updates.set(DeploymentConfig.ENV_VARS, envVars),
                Updates.set(DeploymentConfig.LAST_UPDATED_TS, com.akto.dao.context.Context.now())
            )
        ) != null;
    }

    public boolean addEnvVariable(String deploymentId, DeploymentConfig.EnvVariable envVariable) {
        return instance.updateOne(
            Filters.eq(DeploymentConfig.ID, deploymentId),
            Updates.combine(
                Updates.addToSet(DeploymentConfig.ENV_VARS, envVariable),
                Updates.set(DeploymentConfig.LAST_UPDATED_TS, com.akto.dao.context.Context.now())
            )
        ) != null;
    }

    public boolean removeEnvVariable(String deploymentId, String envKey) {
        return instance.updateOne(
            Filters.eq(DeploymentConfig.ID, deploymentId),
            Updates.combine(
                Updates.pull(DeploymentConfig.ENV_VARS, new BasicDBObject("key", envKey)),
                Updates.set(DeploymentConfig.LAST_UPDATED_TS, com.akto.dao.context.Context.now())
            )
        ) != null;
    }

    public boolean updateEnvVariable(String deploymentId, String envKey, String newValue) {
        return instance.updateOne(
            Filters.and(
                Filters.eq(DeploymentConfig.ID, deploymentId),
                Filters.eq(DeploymentConfig.ENV_VARS + ".key", envKey)
            ),
            Updates.combine(
                Updates.set(DeploymentConfig.ENV_VARS + ".$.value", newValue),
                Updates.set(DeploymentConfig.LAST_UPDATED_TS, com.akto.dao.context.Context.now())
            )
        ) != null;
    }
}
