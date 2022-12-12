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
    importPostmanWorkspace(workspace_id) {
        return request({
            url: '/api/importDataFromPostman',
            method: 'post',
            data: {workspace_id}
        }).then((resp) => {
            return resp
        })
    },
}