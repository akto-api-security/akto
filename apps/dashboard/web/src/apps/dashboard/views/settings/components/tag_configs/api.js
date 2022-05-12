
import request from '@/util/request'

export default {
    fetchTagConfigs() {
        return request({
            url: '/api/fetchTagConfigs',
            method: 'post',
            data: { }
        })
    },

    reviewTagConfig(id, name, keyConditionFromUsers, active, pageNum) {
        return request({
            url: '/api/reviewTagConfig',
            method: 'post',
            data: {
                id,
                name,
                keyConditionFromUsers,
                active,
                pageNum
            }

        })
    },

    saveTagConfig(id,name, keyConditionFromUsers, createNew,active) {
        return request({
            url: '/api/saveTagConfig',
            method: 'post',
            data: {
                id,name,keyConditionFromUsers, createNew, active
             }

        })
    },

    toggleActiveTagConfig(name, active) {
        return request({
            url: '/api/toggleActiveTagConfig',
            method: 'post',
            data: {
                name, active
            }
        })
    },

}