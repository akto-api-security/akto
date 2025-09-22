package com.akto.action.settings;

import com.akto.action.UserAction;
import com.akto.dao.deployment.DeploymentConfigDao;
import com.akto.dto.deployment.DeploymentConfig;
import com.mongodb.BasicDBObject;
import com.mongodb.client.model.Filters;

import java.util.List;
import java.util.ArrayList;

public class DeploymentConfigAction extends UserAction {
    
    private List<DeploymentConfig> deploymentConfigs;
    private String deploymentId;
    private String deploymentName;
    private String deploymentType;
    private List<DeploymentConfig.EnvVariable> envVars;
    private String envKey;
    private String envValue;
    private boolean editable;

    @Override
    public String execute() {
        return SUCCESS;
    }

    public String fetchAllDeploymentConfigs() {
        deploymentConfigs = DeploymentConfigDao.instance.findAllDeployments();
        return SUCCESS.toUpperCase();
    }

    public String addDeploymentConfig() {
        if (deploymentId == null || deploymentName == null || deploymentType == null) {
            return ERROR.toUpperCase();
        }

        DeploymentConfig existingConfig = DeploymentConfigDao.instance.findByName(deploymentName);
        if (existingConfig != null) {
            return ERROR.toUpperCase();
        }

        if (envVars == null) {
            envVars = new ArrayList<>();
        }

        DeploymentConfig deploymentConfig = new DeploymentConfig(deploymentId, deploymentName, deploymentType, envVars);
        DeploymentConfigDao.instance.insertOne(deploymentConfig);
        
        return SUCCESS.toUpperCase();
    }

    public String updateDeploymentConfig() {
        if (deploymentId == null) {
            return ERROR.toUpperCase();
        }

        boolean updated = false;
        if (envVars != null) {
            updated = DeploymentConfigDao.instance.updateEnvVars(deploymentId, envVars);
        }

        return updated ? SUCCESS.toUpperCase() : ERROR.toUpperCase();
    }

    public String addEnvVariable() {
        if (deploymentId == null || envKey == null || envValue == null) {
            return ERROR.toUpperCase();
        }

        DeploymentConfig.EnvVariable envVariable = new DeploymentConfig.EnvVariable(envKey, envValue, editable);
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

    public String deleteDeploymentConfig() {
        if (deploymentId == null) {
            return ERROR.toUpperCase();
        }

        long deleted = DeploymentConfigDao.instance.deleteAll(Filters.eq(DeploymentConfig.ID, deploymentId)).getDeletedCount();
        return deleted > 0 ? SUCCESS.toUpperCase() : ERROR.toUpperCase();
    }

    // Getters and Setters
    public List<DeploymentConfig> getDeploymentConfigs() {
        return deploymentConfigs;
    }

    public void setDeploymentConfigs(List<DeploymentConfig> deploymentConfigs) {
        this.deploymentConfigs = deploymentConfigs;
    }

    public String getDeploymentId() {
        return deploymentId;
    }

    public void setDeploymentId(String deploymentId) {
        this.deploymentId = deploymentId;
    }

    public String getDeploymentName() {
        return deploymentName;
    }

    public void setDeploymentName(String deploymentName) {
        this.deploymentName = deploymentName;
    }

    public String getDeploymentType() {
        return deploymentType;
    }

    public void setDeploymentType(String deploymentType) {
        this.deploymentType = deploymentType;
    }

    public List<DeploymentConfig.EnvVariable> getEnvVars() {
        return envVars;
    }

    public void setEnvVars(List<DeploymentConfig.EnvVariable> envVars) {
        this.envVars = envVars;
    }

    public String getEnvKey() {
        return envKey;
    }

    public void setEnvKey(String envKey) {
        this.envKey = envKey;
    }

    public String getEnvValue() {
        return envValue;
    }

    public void setEnvValue(String envValue) {
        this.envValue = envValue;
    }

    public boolean isEditable() {
        return editable;
    }

    public void setEditable(boolean editable) {
        this.editable = editable;
    }
}
