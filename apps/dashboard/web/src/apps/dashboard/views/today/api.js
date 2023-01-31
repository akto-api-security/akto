import request from '@/util/request'

export default {
    saveContentAndFetchAuditResults(apiSpec) {
        return request({
            url: '/api/saveContentAndFetchAuditResults',
            method: 'post',
            data: {
                apiSpec: apiSpec.content,
                filename: apiSpec.filename
            }
        })
    },

    fetchAuditResults() {
        return request({
            url: '/api/fetchAuditResults',
            method: 'post',
            data: {}
        })
    },

    saveTestConfig({authProtocolSettings, testEnvSettings, authProtocolSpecs, contentId}) {
        return request({
            url: '/api/saveTestConfig',
            method: 'post',
            data: {
                authProtocolSettings, 
                testEnvSettings, 
                authProtocolSpecs,
                contentId
            }
        })
    },

    fetchTestResults({attemptIds}) {
        return request({
            url: '/api/fetchTestResults',
            method: 'post',
            data: {
                attemptIds
            }
        })
    },

    getAPICatalog() {
        return request({
            url: '/api/getAPICatalog',
            method: 'get'
        })
    }

}