import request from '@/util/request'

export default {
    bulkMarkSensitive(items) {
        return request({
            url: '/api/bulkMarkSensitive',
            method: 'post',
            data: {
                items
            }
        })
    }
}