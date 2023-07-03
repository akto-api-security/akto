import request from "../../util/request"

export default {
    login: async function (username, password) {
        const resp = await request({
            url: '/auth/login',
            method: 'post',
            data: {
                username,
                password
            }
        })
        var redirectLink = '/dashboard/testing'
        if (resp.loginResult && resp.loginResult.redirect) {
            redirectLink = resp.loginResult.redirect
        } else {
            var redirectLink = new URLSearchParams(window.location.search).get('redirect_uri') || '/dashboard/testing'
            if (!redirectLink.startsWith('/dashboard/')) {
                redirectLink = '/dashboard/testing'
            }
        }
        window.location.href = redirectLink
    }
}