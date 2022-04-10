
import request from '@/util/request'

export default {
    fetchDataTypes() {
        return request({
            url: '/api/fetchDataTypes',
            method: 'post',
            data: { }
        })
    },

    reviewCustomDataType(id,name,sensitiveAlways,operator,keyOperator, keyConditionFromUsers,valueOperator ,valueConditionFromUsers, createNew) {
        return request({
            url: '/api/reviewCustomDataType',
            method: 'post',
            data: {
                id,
                name,
                sensitiveAlways,
                operator,
                keyOperator,
                keyConditionFromUsers,
                valueOperator,
                valueConditionFromUsers,
                createNew
            }

        })
    },

    saveCustomDataType(id,name,sensitiveAlways,operator,keyOperator, keyConditionFromUsers,valueOperator ,valueConditionFromUsers, createNew) {
        return request({
            url: '/api/saveCustomDataType',
            method: 'post',
            data: {
                id,name,sensitiveAlways,operator,keyOperator, keyConditionFromUsers,valueOperator ,valueConditionFromUsers, createNew
             }

        })
    },

}