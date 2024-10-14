import request from "@/util/request"

const homeRequests = {
    getCollections: async () => {
        const resp = await request({
            url: '/api/getAllCollections',
            method: 'post',
            data: {}
        })
        return resp
    },
    getTrafficAlerts(){
        return request({
            url: '/api/getAllTrafficAlerts',
            method: 'post',
            data: {}
        })
    },
    markAlertAsDismissed(trafficAlert){
        return request({
            url: '/api/markAlertAsDismissed',
            method: 'post',
            data: {trafficAlert}
        })
    },
    getEventForIntercom : async() => {
        return await request({
            url: '/api/send_event',
            method: 'post',
            data: {}
        })
    },
    updateUsernameAndOrganization: async(username, organization) => {
        return await request({
            url: 'api/updateUsernameAndOrganization',
            method: 'post',
            data: { username, organization}
        })
    }
}

export default homeRequests