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
    getEventForIntercom : async() => {
        return await request({
            url: '/api/send_event',
            method: 'post',
            data: {}
        })
    }
}

export default homeRequests