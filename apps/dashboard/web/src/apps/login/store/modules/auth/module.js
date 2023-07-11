import Vue from 'vue'
import Vuex from 'vuex'
import request from '@/util/request'
import apiNav from "@/apps/dashboard/nav/api";

Vue.use(Vuex)

var state = {
    access_token: null,
    expires_in: 3600,
    token_type: 'bearer',
    username: window.USER_NAME,
    avatar: window.AVATAR,
    accounts: window.ACCOUNTS || [],
    activeAccount: window.ACTIVE_ACCOUNT,
    users: window.USERS || []
}

const auth = {
    namespaced: true,
    state: state,
    mutations: {
        SET_LOGIN(state, { accessToken, expires_in }) {
            state.access_token = accessToken
            state.expires_in = expires_in
        },
        SET_ACCESS_TOKEN(state, token) {
            state.access_token = token
        },
        SET_LOGIN_PROFILE(state, payload) {
            state.username = payload.username
            state.avatar = payload.avatar
            state.accounts = payload.accounts
            state.activeAccount = payload.activeAccount
            state.users = payload.users
        }
    },
    actions: {
        login({ commit, dispatch }, { username, password, accountId }) {
            return request({
                url: '/auth/login',
                method: 'post',
                data: {
                    username,
                    password
                }
            }).then((resp, a, b, c) => {
                var redirectLink = '/dashboard/observe/inventory'
                if (resp.loginResult && resp.loginResult.redirect) {
                    redirectLink = resp.loginResult.redirect
                } else {
                    var redirectLink = new URLSearchParams(window.location.search).get('redirect_uri') || '/dashboard/observe/inventory'
                    if (!redirectLink.startsWith('/dashboard/')) {
                        redirectLink = '/dashboard/observe/inventory'
                    }
                }
                window.location.href = redirectLink
            })
        },
        logout({ commit }) {
            commit('SET_ACCESS_TOKEN', null)
        },
        // get current login user info

        fetchProfile({ commit }, accountId) {
            return request({
                url: '/api/me',
                method: 'post',
                data: {
                    type: "all",
                    accountId: accountId
                }
            })
        },

        addNewTeam({commit}, {name}) {
            return apiNav.createNewTeam(name).then(resp => {
                commit('ADD_TEAM', {name, id: resp.id})
                return resp
            })
        }
    },
    getters: {
        getAccessToken: (state) => state.access_token,
        getAvatar: (state) => state.avatar,
        getUsername: (state) => state.username,
        getAccounts: (state) => state.accounts,
        getActiveAccount: (state) => state.activeAccount,
        getActiveAccountId: (state) => (state.activeAccount && state.activeAccount.accountId) || 0
    }
}

export default auth