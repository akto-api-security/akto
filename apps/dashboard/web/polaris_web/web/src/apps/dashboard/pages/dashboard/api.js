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
    }
}

export default api;