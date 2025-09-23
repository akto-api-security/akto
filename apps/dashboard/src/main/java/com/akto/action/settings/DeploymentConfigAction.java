package com.akto.action.settings;

import com.akto.action.UserAction;
import com.akto.dao.deployment.DeploymentConfigDao;
import com.akto.dto.deployment.DeploymentConfig;
import com.akto.dto.deployment.EnvVariable;
import com.mongodb.client.model.Filters;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

public class DeploymentConfigAction extends UserAction {
    
    @Getter @Setter private List<DeploymentConfig> deploymentConfigs;
    @Getter @Setter private String deploymentId;
    @Getter @Setter private String deploymentName;
    @Getter @Setter private String deploymentType;
    @Getter @Setter private List<EnvVariable> envVars;
    @Getter @Setter private String envKey;
    @Getter @Setter private String envValue;
    @Getter @Setter private boolean editable;

    @Override
    public String execute() {
        return SUCCESS;
    }

    public String fetchAllDeploymentConfigs() {
        deploymentConfigs = DeploymentConfigDao.instance.findAllDeployments();
        return SUCCESS.toUpperCase();
    }

    public String addEnvVariable() {
        if (deploymentId == null || envKey == null || envValue == null) {
            return ERROR.toUpperCase();
        }
        EnvVariable envVariable = new EnvVariable(envKey, envValue, editable);
        boolean added = DeploymentConfigDao.instance.addEnvVariable(deploymentId, envVariable);
        return added ? SUCCESS.toUpperCase() : ERROR.toUpperCase();
    }

    public String updateEnvVariable() {
        if (deploymentId == null || envKey == null || envValue == null) {
            return ERROR.toUpperCase();
        }
        boolean updated = DeploymentConfigDao.instance.updateEnvVariable(deploymentId, envKey, envValue);
        return updated ? SUCCESS.toUpperCase() : ERROR.toUpperCase();
    }

    public String removeEnvVariable() {
        if (deploymentId == null || envKey == null) {
            return ERROR.toUpperCase();
        }
        boolean removed = DeploymentConfigDao.instance.removeEnvVariable(deploymentId, envKey);
        return removed ? SUCCESS.toUpperCase() : ERROR.toUpperCase();
    }
}
