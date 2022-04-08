
import request from '@/util/request'

export default {
    fetchDataTypes() {
        return request({
            url: '/api/fetchDataTypes',
            method: 'post',
            data: { }
        })
    },



}