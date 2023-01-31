import request from '@/util/request'


export default {
    createNewTeam: function (name) {
        return request({
            url: 'api/createNewTeam',
            method: 'post',
            data: {
                name
            }
        })
    }
}