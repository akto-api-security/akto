import request from '@/util/request'

export default {
    fetchIssues() {
        return request({
            url: 'api/fetchAllIssues',
            method: 'post',
            data: {}
        })
    }
}