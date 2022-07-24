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
        dashboardVersion: null,
        apiRuntimeVersion: null,
        setupType: null
    },
    getters: {
        getId: (state) => state.id,
        getUsers: (state) => state.users,
        getName: (state) => state.name,
        getTeam: (state) => state.team,
        getRedactPayload: (state) => state.redactPayload,
        getDashboardVersion: (state) => state.dashboardVersion,
        getApiRuntimeVersion: (state) => state.apiRuntimeVersion,
        getSetupType: (state) => state.setupType
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
            } else {
                state.redactPayload = resp.accountSettings.redactPayload ? resp.accountSettings.redactPayload : false
                state.apiRuntimeVersion = resp.accountSettings.apiRuntimeVersion ? resp.accountSettings.apiRuntimeVersion : "-"
                state.dashboardVersion = resp.accountSettings.dashboardVersion ? resp.accountSettings.dashboardVersion : "-"
                state.setupType = "STAGING"
                state.redactPayload = resp.accountSettings.redactPayload ? resp.accountSettings.redactPayload : false
                state.setupType = resp.accountSettings.setupType
            }
        },
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
        toggleRedactFeature({commit, dispatch, state}, v) {
            api.toggleRedactFeature(v).then((resp => {
                state.redactPayload = v
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