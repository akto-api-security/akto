
import request from '@/util/request'

export default {
    fetchCustomAuthTypes() {
        return request({
            url: '/api/fetchCustomAuthTypes',
            method: 'post',
            data: { }
        })
    },

    addCustomAuthType(name, operator, keys, active) {
        return request({
            url: '/api/addCustomAuthType',
            method: 'post',
            data: {
                name,
                operator,
                keys,
                active
            }

        })
    },

    updateCustomAuthType(name, operator, keys, active) {
        return request({
            url: '/api/updateCustomAuthType',
            method: 'post',
            data: {
                name,
                operator,
                keys,
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