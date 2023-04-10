import request from '@/util/request'

export default{
    addBurpToken() {
        return request({
            url: '/api/addBurpToken',
            method: 'post',
            data: {}
        }).then((resp) => {
            return resp
        })
    },
    deleteApiToken(apiTokenId) {
        return request({
            url: '/api/deleteApiToken',
            method: 'post',
            data: {apiTokenId}
        }).then((resp) => {
            return resp
        })
    },
    fetchQuickStartPageState() {
        return request({
            url: '/api/fetchQuickStartPageState',
            method: 'post',
            data: {}
        }).then((resp) => {
            return resp
        })
    },
    fetchApiTokens() {
        return request({
            url: '/api/fetchApiTokens',
            method: 'post',
            data: {}
        }).then((resp) => {
            return resp
        })
    },
    fetchLBs(){
        return request({
            url: '/api/fetchLoadBalancers',
            method: 'post',
            data: {}
        })
    },
    saveLBs(selectedLBs){
        return request({
            url: 'api/saveLoadBalancers',
            method: 'post',
            data: {selectedLBs}
        })
    },
    fetchStackCreationStatus(){
        return request({
            url: 'api/checkStackCreationProgress',
            method: 'post',
            data: {}
        })
    },
    getPostmanCredentials() {
        return request({
            url: '/api/getPostmanCredential',
            method: 'post',
            data: {}
        }).then((resp) => {
            return resp
        })
    },
    fetchPostmanWorkspaces(api_key) {
        return request({
            url: '/api/fetchPostmanWorkspaces',
            method: 'post',
            data: {api_key}
        }).then((resp) => {
            return resp
        })
    },
    importPostmanWorkspace(workspace_id, allowReplay, api_key) {
        return request({
            url: '/api/importPostmanWorkspace',
            method: 'post',
            data: {workspace_id, allowReplay, api_key}
        }).then((resp) => {
            return resp
        })
    },
    importDataFromPostmanFile(postmanCollectionFile, allowReplay) {
        return request({
            url: '/api/importDataFromPostmanFile',
            method: 'post',
            data: {postmanCollectionFile, allowReplay}
        }).then((resp) => {
            return resp
        })
    },
    fetchBurpPluginInfo() {
        return request({
            url: '/api/fetchBurpPluginInfo',
            method: 'post',
            data: {}
        }).then((resp) => {
            return resp
        })
    },
    downloadBurpPluginJar() {
        return request({
            url: '/api/downloadBurpPluginJar',
            method: 'post',
            data: {},
            responseType: 'blob'
        }).then((resp) => {
            return resp
        })
    }
}