
import request from '@/util/request'

export default {
    fetchCustomAuthTypes() {
        return request({
            url: '/api/fetchCustomAuthTypes',
            method: 'post',
            data: { }
        })
    },

    addCustomAuthType(name, headerKeys, payloadKeys, active) {
        return request({
            url: '/api/addCustomAuthType',
            method: 'post',
            data: {
                name,
                headerKeys,
                payloadKeys,
                active
            }

        })
    },

    updateCustomAuthType(name, headerKeys, payloadKeys, active) {
        return request({
            url: '/api/updateCustomAuthType',
            method: 'post',
            data: {
                name,
                headerKeys,
                payloadKeys,
                active
             }

        })
    },

    updateCustomAuthTypeStatus(name, active) {
        return request({
            url: '/api/updateCustomAuthTypeStatus',
            method: 'post',
            data: {
                name, active
            }
        })
    },

}