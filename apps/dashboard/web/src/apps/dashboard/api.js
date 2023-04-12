import request from '@/util/request'

export default {
    fetchActiveLoaders: function () {
        return request({
            url: '/api/fetchActiveLoaders',
            method: 'post',
            data: {}
        })
    },

    closeLoader: function (hexId) {
        return request({
            url: '/api/closeLoader',
            method: 'post',
            data: {hexId}
        })
    }
}
