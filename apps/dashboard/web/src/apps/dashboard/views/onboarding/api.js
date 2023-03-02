import request from '@/util/request'

export default {
    fetchAuthMechanismData() {
        return request({
            url: '/api/fetchAuthMechanismData',
            method: 'post',
            data: {}
        }).then((resp) => {
            return resp
        })
    },

    runTestOnboarding(authParamData,collectionId,testSuite) {
        return request({
            url: '/api/runTestOnboarding',
            method: 'post',
            data: {
                authParamData, collectionId, testSuite
            }
        }).then((resp) => {
            return resp
        })
    },
}