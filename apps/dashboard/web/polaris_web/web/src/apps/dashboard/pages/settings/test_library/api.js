import request from "@/util/request"

const testLibraryRequests = {
    addTestLibrary(repositoryUrl) {
        return request({
            url: '/api/addTestLibrary',
            method: 'post',
            data: {repositoryUrl: repositoryUrl}
        })
    },
    removeTestLibrary(repositoryUrl) {
        return request({
            url: '/api/removeTestLibrary',
            method: 'post',
            data: {repositoryUrl: repositoryUrl}
        })
    },
    syncCustomLibrary(repositoryUrl) {
        return request({
            url: '/api/syncCustomLibrary',
            method: 'post',
            data: {repositoryUrl: repositoryUrl}
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