import request from "@/util/request"

const authTypesApi = {
    resetAllCustomAuthTypes() {
        return request({
            url: '/api/resetAllCustomAuthTypes',
            method: 'post',
            data: {}
        })
    },
    fetchCustomAuthTypes() {
        return request({
            url: '/api/fetchCustomAuthTypes',
            method: 'post',
            data: { }
        })
    },
    addCustomAuthType(name, headerKeys, payloadKeys, active) {
        return request({
            url: '/api/addCustomAuthType',
            method: 'post',
            data: {
                name,
                headerKeys,
                payloadKeys,
                active
            }

        })
    },
    updateCustomAuthType(name, headerKeys, payloadKeys, active) {
        return request({
            url: '/api/updateCustomAuthType',
            method: 'post',
            data: {
                name,
                headerKeys,
                payloadKeys,
                active
             }

        })
    }
}

export default authTypesApi