import request from "@/util/request"

const trafficFiltersRequest = {
    getAdvancedFiltersForTraffic() {
        return request({
            url: '/api/fetchAdvancedFiltersForTraffic',
            method: 'post',
            data: {}
        })
    },
    updateAdvancedFiltersForTraffic(yamlContent) {
        return request({
            url: '/api/addAdvancedFiltersForTraffic',
            method: 'post',
            data: {yamlContent}
        })
    },
    deleteFilter(id) {
        return request({
            url: '/api/deleteAdvancedFilter',
            method: 'post',
            data: {templateId: id}
        })
    },
    changeStateOfFilter(id, inactive) {
        return request({
            url: '/api/changeStateOfFilter',
            method: 'post',
            data: {templateId: id, inactive: inactive}
        })
    },
    dryRunAdvancedFilters(yamlContent, deleteAPIsInstantly){
        return request({
            url: '/api/dryRunAdvancedFilters',
            method: 'post',
            data: {yamlContent, deleteAPIsInstantly}
        })
    },
    cleanUpInventory(deleteAPIsInstantly){
        return request({
            url: '/api/cleanNonHostApiInfos',
            method: 'post',
            data: {deleteAPIsInstantly}
        })
    },
    getAdvancedFiltersPermissions(){
        return request({
            url: '/api/getAccountSettingsForAdvancedFilters',
            method: 'post',
            data: {}
        })
    },
    updateAdvancedFiltersPermissions(updateFiltersFlag, permissionValue){
        return request({
            url: '/api/updateRetrospectiveFilterSettings',
            method: 'post',
            data: {updateFiltersFlag, permissionValue}
        })
    },
    deleteOptionAndSlashApis(deleteAPIsInstantly){
        return request({
            url: '/api/deleteOptionAndSlashApis',
            method: 'post',
            data: {deleteAPIsInstantly}
        })
    }
}

export default trafficFiltersRequest