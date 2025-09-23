import request from "@/util/request"

const deploymentConfigApi = {
    fetchAllDeploymentConfigs() {
        return request({
            url: '/api/fetchAllDeploymentConfigs',
            method: 'post',
            data: {}
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
    }
}

export default deploymentConfigApi
