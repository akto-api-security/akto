import request from '@/util/request'

export default {
    fetchTrafficMetrics(groupBy, startTimestamp, endTimestamp,names, host) {
        return request({
            url: '/api/fetchTrafficMetrics',
            method: 'post',
            data: {groupBy, startTimestamp, endTimestamp, names, host}
        }).then((resp) => {
            return resp
        })
    },
    fetchTrafficMetricsDesciptions() {
        return request({
            url: '/api/fetchTrafficMetricsDesciptions',
            method: 'post',
            data: {}
        }).then((resp) => {
            return resp
        })
    },
}