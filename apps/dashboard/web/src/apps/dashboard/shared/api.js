import request from '@/util/request'

export default {
    toggleFavourite: function (newStatus, id, type) {
        return request({
            url: '/api/toggleFavourite',
            method: 'post',
            data: {
                preference: newStatus ? 1 : 0,
                parent_id: id,
                type
            }
        })
    },
    sendGoogleAuthCodeToServer (code) {
        return request({
            url: 'api/sendGoogleAuthCodeToServer',
            method: 'post',
            data: {
                code: code
            }
        })
    },
    askAi(data){
        return request({
            url: '/api/ask_ai',
            method: 'post',
            data: data
        })
    },
    fetchTestRoles(){
        return request({
            url: '/api/fetchTestRoles',
            method: 'post'
        })
    }

}