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
        var redirectLink = '/dashboard/home'
        if (resp.loginResult && resp.loginResult.redirect) {
            redirectLink = resp.loginResult.redirect
        } else {
            var redirectLink = new URLSearchParams(window.location.search).get('redirect_uri') || '/dashboard/home'
            if (!redirectLink.startsWith('/dashboard/')) {
                redirectLink = '/dashboard/home'
            }
        }
        window.location.href = redirectLink
    },
    logout: async function () {
        const res = await request({
            url: '/api/logout',
            method: 'post',
            data: {}
        })
        return res
    },
    updateAktoUIMode(aktoUIMode) {
        return request({
            url: 'api/updateAktoUIMode',
            method: 'post',
            data: aktoUIMode
        })
    },
    goToAccount: function (newAccountId) {
        return request({
            url: '/api/goToAccount',
            method: 'post',
            data: {
                newAccountId
            }
        })
    },
    saveToAccount: function (newAccountName) {
        return request({
            url: '/api/createNewAccount',
            method: 'post',
            data: {
                newAccountName
            }
        })
    },
    signupUser: function(email, password, invitationCode){
        return request({
            url: '/signup-email',
            method: 'post',
            data: {
                email, password, invitationCode
            }
        })
    },
    triggerGoogleSSO: function(userEmail){
        return request({
            url: '/trigger-google-sso',
            method: 'post',
            data: {
                userEmail
            }
        })
    },
    sendPasswordResetLink: function(email) {
        return request({
            url: 'auth/sendPasswordResetLink',
            method: 'post',
            data: {
                forgotPasswordEmail: email
            }
        })
    },
    resetPassword: function(token, newPassword) {
        return request({
            url: 'auth/resetPassword',
            method: 'post',
            data: {
                resetPasswordToken: token,
                newPassword
            }
        })
    }
}