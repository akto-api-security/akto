import request from "@/util/request"

const testLibraryRequests = {
    addTestLibrary(data) {
        return request({
            url: '/api/addTestLibrary',
            method: 'post',
            data: data
        })
    },
    removeTestLibrary(data) {
        return request({
            url: '/api/removeTestLibrary',
            method: 'post',
            data: data
        })
    },
    syncCustomLibrary(data) {
        return request({
            url: '/api/syncCustomLibrary',
            method: 'post',
            data: data
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