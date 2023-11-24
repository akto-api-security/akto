import request from "@/util/request";
const api = {
    getRiskScoreRangeMap: async() =>{
        return await request({
            url: '/api/getRiskScoreRangeMap',
            method: 'post',
            data: {}
        })
    },
    
    getIssuesTrend: async(startTimeStamp,endTimeStamp) =>{
        return await request({
            url: '/api/getIssuesTrend',
            method: 'post',
            data: {startTimeStamp,endTimeStamp}
        })
    },

    fetchSubTypeCountMap(startTimestamp, endTimestamp) {
        return request({
            url: '/api/fetchSubTypeCountMap',
            method: 'post',
            data: {
                startTimestamp, 
                endTimestamp
            }
        })
    },

    fetchRecentFeed(skip){
        return request({
            url: '/api/getRecentActivities',
            method: 'post',
            data: {
                skip
            }
        })
    }
}

export default api;