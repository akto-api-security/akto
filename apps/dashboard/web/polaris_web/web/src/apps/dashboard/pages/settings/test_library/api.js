import request from "@/util/request"

const testLibraryRequests = {
    addTestLibrary(repositoryUrl, overrideSystemTemplates) {
        return request({
            url: '/api/addTestLibrary',
            method: 'post',
            data: {repositoryUrl: repositoryUrl, overrideSystemTemplates: overrideSystemTemplates}
        })
    },
    removeTestLibrary(repositoryUrl, overrideSystemTemplates) {
        return request({
            url: '/api/removeTestLibrary',
            method: 'post',
            data: {repositoryUrl: repositoryUrl, overrideSystemTemplates: overrideSystemTemplates}
        })
    },
    syncCustomLibrary(repositoryUrl, overrideSystemTemplates) {
        return request({
            url: '/api/syncCustomLibrary',
            method: 'post',
            data: {repositoryUrl: repositoryUrl, overrideSystemTemplates: overrideSystemTemplates}
        })
    },
    syncAllDefaultTestLibraries() {
        return request({
            url: '/api/syncAllDefaultTestLibraries',
            method: 'post',
            data: {}
        })
    },
    fetchCustomTestsCount() {
        return request({
            url: '/api/fetchCustomTestsCount',
            method: 'post',
            data: {}
        })
    }
    
}

export default testLibraryRequests
