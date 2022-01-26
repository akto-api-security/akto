import request from '@/util/request'
import util from '../util'

export default {
    saveToAccount: function (newAccountName, obj) {
        return request({
            url: '/api/createNewAccount',
            method: 'post',
            data: {
                newAccountName
            }
        })
    },
    goToAccount: function (newAccountId) {
        return request({
            url: '/api/goToAccount',
            method: 'post',
            data: {
                newAccountId
            }
        }).then(resp => {
            window.location.href = '/dashboard/testing'
        })
    },
    logout() {
        return request({
            url: '/api/logout',
            method: 'post',
            data: {}
        }).then((resp) => {
            return resp
        })
    },
}