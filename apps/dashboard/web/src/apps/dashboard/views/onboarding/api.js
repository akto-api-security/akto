import request from '@/util/request'

export default {
    fetchActiveTestingDetails() {
        return request({
            url: '/api/retrieveAllCollectionTests',
            method: 'post',
            data: {}
        }).then((resp) => {
            return resp
        })
    },
}