import request from '@/util/request'

export default {
    fetchParamsStatus() {
        return request({
            url: '/api/fetchParamsStatus',
            method: 'post',
            data: {}
        })
    },
}
