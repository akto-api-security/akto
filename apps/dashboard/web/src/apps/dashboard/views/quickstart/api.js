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
        console.log(selectedLBs);
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
    }
}