import request from '@/util/request'

const api = {
    fetchTestSuites(){
        return request({
            url: '/api/fetchTestSuites',
            method: 'post',
            data: {}
        })
    },
}

export default api