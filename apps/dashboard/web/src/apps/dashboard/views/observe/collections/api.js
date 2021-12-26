import request from '@/util/request'

export default {
    getAllCollections () {
        return request({
            url: '/api/getAllCollections',
            method: 'post',
            data: {}
        }).then((resp) => {
            return resp
        })
    }
}