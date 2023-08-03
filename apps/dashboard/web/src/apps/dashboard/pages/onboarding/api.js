import request from '@/util/request'

const api = {
    fetchTestSuites(){
        return request({
            url: '/api/fetchTestSuites',
            method: 'post',
            data: {}
        })
    },

    runTestOnboarding(authParamData,collectionId,testSuite) {
        return request({
            url: '/api/runTestOnboarding',
            method: 'post',
            data: {
                authParamData, collectionId, testSuite
            }
        })
    },

    skipOnboarding() {
        return request({
            url: '/api/skipOnboarding',
            method: 'post',
            data: {}
        })
    }
}

export default api