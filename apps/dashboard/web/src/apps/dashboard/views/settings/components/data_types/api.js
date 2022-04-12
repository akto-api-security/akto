
import request from '@/util/request'

export default {
    fetchDataTypes() {
        return request({
            url: '/api/fetchDataTypes',
            method: 'post',
            data: { }
        })
    },

    reviewCustomDataType(id,name,sensitiveAlways,operator,keyOperator, keyConditionFromUsers,valueOperator ,
                         valueConditionFromUsers,active, pageNum) {
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
                active,
                pageNum
            }

        })
    },

    saveCustomDataType(id,name,sensitiveAlways,operator,keyOperator, keyConditionFromUsers,valueOperator ,valueConditionFromUsers, createNew,active) {
        return request({
            url: '/api/saveCustomDataType',
            method: 'post',
            data: {
                id,name,sensitiveAlways,operator,keyOperator, keyConditionFromUsers,valueOperator ,valueConditionFromUsers, createNew, active
             }

        })
    },

    toggleActiveParam(name, active) {
        return request({
            url: '/api/toggleDataTypeActiveParam',
            method: 'post',
            data: {
                name, active
            }

        })
    },

}