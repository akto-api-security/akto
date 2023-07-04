import request from "../../../../util/request"

export default {
    fetchTestRunTableInfo() {
        return request({
            url: '/api/fetchTestRunTableInfo',
            method: 'post',
            data: {}
        }).then((resp) => {
            return resp
        })
    },
}