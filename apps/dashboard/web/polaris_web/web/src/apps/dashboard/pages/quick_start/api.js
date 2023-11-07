import request from '@/util/request'

const api = {
    fetchQuickStartPageState() {
        return request({
            url: '/api/fetchQuickStartPageState',
            method: 'post',
            data: {}
        })
    },
    
    fetchBurpPluginInfo() {
        return request({
            url: '/api/fetchBurpPluginInfo',
            method: 'post',
            data: {}
        })
    },
    downloadBurpPluginJar() {
        return request({
            url: '/api/downloadBurpPluginJar',
            method: 'post',
            data: {},
            responseType: 'blob'
        })
    },

    importDataFromPostmanFile(postmanCollectionFile, allowReplay) {
        return request({
            url: '/api/importDataFromPostmanFile',
            method: 'post',
            data: {postmanCollectionFile, allowReplay}
        })
    },
    importPostmanWorkspace(workspace_id, allowReplay, api_key) {
        return request({
            url: '/api/importPostmanWorkspace',
            method: 'post',
            data: {workspace_id, allowReplay, api_key}
        })
    },

    fetchLBs(data){
        return request({
            url: '/api/fetchLoadBalancers',
            method: 'post',
            data: data,
        })
    },
    saveLBs(selectedLBs){
        return request({
            url: 'api/saveLoadBalancers',
            method: 'post',
            data: {selectedLBs}
        })
    },
    fetchStackCreationStatus(data){
        return request({
            url: 'api/checkStackCreationProgress',
            method: 'post',
            data: data,
        })
    },
    createRuntimeStack(deploymentMethod) {
        return request({
            url: '/api/createRuntimeStack',
            method: 'post',
            data: {deploymentMethod}
        })
    },
    fetchBurpPluginDownloadLink() {
        return request({
            url: '/api/fetchBurpPluginDownloadLink',
            method: 'post',
            data: {},
        }).then((resp) => {
            return resp
        })
    },
    fetchBurpCredentials() {
        return request({
            url: '/api/fetchBurpCredentials',
            method: 'post',
            data: {},
        }).then((resp) => {
            return resp
        })
    }
}

export default api