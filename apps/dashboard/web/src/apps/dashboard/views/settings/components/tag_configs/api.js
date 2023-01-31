
import request from '@/util/request'

export default {
    fetchTagConfigs() {
        return request({
            url: '/api/fetchTagConfigs',
            method: 'post',
            data: { }
        })
    },

    reviewTagConfig(id, name, keyOperator, keyConditionFromUsers, active, pageNum) {
        return request({
            url: '/api/reviewTagConfig',
            method: 'post',
            data: {
                id,
                name,
                keyOperator,
                keyConditionFromUsers,
                active,
                pageNum
            }

        })
    },

    saveTagConfig(id,name, keyOperator, keyConditionFromUsers, createNew,active) {
        return request({
            url: '/api/saveTagConfig',
            method: 'post',
            data: {
                id,name,keyOperator,keyConditionFromUsers, createNew, active
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