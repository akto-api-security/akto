import request from "@/util/request"

const deploymentConfigApi = {
    fetchAllDeploymentConfigs() {
        return request({
            url: '/api/fetchAllDeploymentConfigs',
            method: 'post',
            data: {}
        })
    },

    addDeploymentConfig(deploymentId, deploymentName, deploymentType, envVars = []) {
        return request({
            url: '/api/addDeploymentConfig',
            method: 'post',
            data: {
                deploymentId,
                deploymentName,
                deploymentType,
                envVars
            }
        })
    },

    updateDeploymentConfig(deploymentId, envVars) {
        return request({
            url: '/api/updateDeploymentConfig',
            method: 'post',
            data: {
                deploymentId,
                envVars
            }
        })
    },

    addEnvVariable(deploymentId, envKey, envValue, editable = true) {
        return request({
            url: '/api/addEnvVariable',
            method: 'post',
            data: {
                deploymentId,
                envKey,
                envValue,
                editable
            }
        })
    },

    updateEnvVariable(deploymentId, envKey, envValue) {
        return request({
            url: '/api/updateEnvVariable',
            method: 'post',
            data: {
                deploymentId,
                envKey,
                envValue
            }
        })
    },

    removeEnvVariable(deploymentId, envKey) {
        return request({
            url: '/api/removeEnvVariable',
            method: 'post',
            data: {
                deploymentId,
                envKey
            }
        })
    },

    deleteDeploymentConfig(deploymentId) {
        return request({
            url: '/api/deleteDeploymentConfig',
            method: 'post',
            data: {
                deploymentId
            }
        })
    }
}

export default deploymentConfigApi
