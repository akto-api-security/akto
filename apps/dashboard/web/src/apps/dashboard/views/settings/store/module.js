import Vue from 'vue'
import Vuex from 'vuex'
import api from '../api'


Vue.use(Vuex)

const team = {
    namespaced: true,
    state: {
        id: null,
        users: {},
        name: '',
        fetchTs: 0,
        team: {},
        redactPayload: null,
        mergeAsyncOutside: null,
        dashboardVersion: null,
        apiRuntimeVersion: null,
        setupType: null,
        lastLoginTs: null,
        privateCidrList: null
    },
    getters: {
        getId: (state) => state.id,
        getUsers: (state) => state.users,
        getName: (state) => state.name,
        getTeam: (state) => state.team,
        getRedactPayload: (state) => state.redactPayload,
        getDashboardVersion: (state) => state.dashboardVersion,
        getApiRuntimeVersion: (state) => state.apiRuntimeVersion,
        getSetupType: (state) => state.setupType,
        getLastLoginTs: (state) => state.lastLoginTs,
        getPrivateCidrList: (state) => state.privateCidrList
    },
    mutations: {
        SET_TEAM_DETAILS(state, details) {
            state.id = details.id
            state.users = details.users
            state.name = details.name
            state.fetchTs = parseInt(new Date().getTime()/1000)
            state.team = details.team
        },
        EMPTY_STATE(state) {
            state.id = null
            state.users = {}
            state.name = ''
            state.fetchTs = 0
            state.team = {}
        },
        SET_ADMIN_SETTINGS(state, resp) {
            if (!resp.accountSettings) {
                state.redactPayload = false
                state.apiRuntimeVersion = "-"
                state.dashboardVersion = "-"
                state.setupType = "PROD"
                state.mergeAsyncOutside = false
            } else {
                state.redactPayload = resp.accountSettings.redactPayload ? resp.accountSettings.redactPayload : false
                state.apiRuntimeVersion = resp.accountSettings.apiRuntimeVersion ? resp.accountSettings.apiRuntimeVersion : "-"
                state.dashboardVersion = resp.accountSettings.dashboardVersion ? resp.accountSettings.dashboardVersion : "-"
                state.redactPayload = resp.accountSettings.redactPayload ? resp.accountSettings.redactPayload : false
                state.setupType = resp.accountSettings.setupType
                state.mergeAsyncOutside = resp.accountSettings.mergeAsyncOutside || false
                state.privateCidrList = resp.accountSettings.privateCidrList
            }
        },
        SET_USER_INFO(state, resp) {
            state.lastLoginTs = resp.lastLoginTs
        }
    },
    actions: {
        getTeamData({commit, dispatch}, id) {
            return api.getTeamData().then((resp) => {
                commit('SET_TEAM_DETAILS', resp)
            })
        },
        fetchAdminSettings({commit, dispatch}) {
            api.fetchAdminSettings().then((resp => {
                commit('SET_ADMIN_SETTINGS', resp)
            }))
        },
        fetchUserLastLoginTs({commit, dispatch}) {
            api.fetchUserLastLoginTs().then((resp => {
                commit('SET_USER_INFO', resp)
            }))
        },
        toggleRedactFeature({commit, dispatch, state}, v) {
            api.toggleRedactFeature(v).then((resp => {
                state.redactPayload = v
            }))
        },
        updateMergeAsyncOutside({commit, dispatch, state}) {
            api.updateMergeAsyncOutside().then((resp => {
                state.mergeAsyncOutside = true
            }))
        },
        updateSetupType({commit, dispatch, state}, v) {
            api.updateSetupType(v).then((resp => {
                state.setupType = v
                window._AKTO.$emit('SHOW_SNACKBAR', {
                    show: true,
                    text: "Setup type updated successfully",
                    color: 'green'
                })
            }))
        },
        removeUser({commit, dispatch}, user) {
            return api.removeUser(user.login).then(resp => {
                api.getTeamData().then((resp) => {
                    commit('SET_TEAM_DETAILS', resp)
                })
                return resp
            })
        },
        emptyState({commit, dispatch}) {
            commit('EMPTY_STATE')
        }
    }
}

export default team