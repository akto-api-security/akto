import request from '@/util/request'

export default {
    getTeamData (teamId) {
        return request({
            url: '/api/getTeamData',
            method: 'post',
            data: {
                id: teamId
            }
        }).then((resp) => {
            return resp
        })
    },
}