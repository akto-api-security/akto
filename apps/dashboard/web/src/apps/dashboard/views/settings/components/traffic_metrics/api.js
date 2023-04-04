import request from '@/util/request'

export default {
    fetchTrafficMetrics(groupBy, startTimestamp, endTimestamp,names) {
        return request({
            url: '/api/fetchTrafficMetrics',
            method: 'post',
            data: {groupBy, startTimestamp, endTimestamp, names}
        }).then((resp) => {
            return resp
        })
    },
}