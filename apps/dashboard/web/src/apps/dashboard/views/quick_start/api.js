import request from '@/util/request'

export default {
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