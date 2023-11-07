import request from "@/util/request"

const homeRequests = {
    getCollections: async () => {
        const resp = await request({
            url: '/api/getAllCollections',
            method: 'post',
            data: {}
        })
        return resp
    }
}

export default homeRequests