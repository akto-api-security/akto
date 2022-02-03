import request from '@/util/request'

export default {
    bulkMarkSensitive(sensitive, items) {
        return request({
            url: '/api/bulkMarkSensitive',
            method: 'post',
            data: {
                items,
                sensitive
            }
        })
    }
}