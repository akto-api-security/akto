import request from "@/util/request"

const tagsApi = {
    fetchTagConfigs() {
        return request({
            url: '/api/fetchTagConfigs',
            method: 'post',
            data: { }
        })
    },
    saveTagConfig(name, keyOperator, keyConditionFromUsers, createNew,active) {
        return request({
            url: '/api/saveTagConfig',
            method: 'post',
            data: {
                name,keyOperator,keyConditionFromUsers, createNew, active
             }

        })
    },
}

export default tagsApi