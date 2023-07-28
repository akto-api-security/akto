import request from "@/util/request"

const TagsApi = {
    fetchTagConfigs() {
        return request({
            url: '/api/fetchTagConfigs',
            method: 'post',
            data: { }
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
}

export default TagsApi