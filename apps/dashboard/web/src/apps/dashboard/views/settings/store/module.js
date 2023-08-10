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
        urlRegexMatchingEnabled: null,
        setupType: null,
        lastLoginTs: null,
        privateCidrList: null,
        enableDebugLogs: null,
        filterHeaderValueMap: null,
        apiCollectionNameMapper: null
    },
    getters: {
        getId: (state) => state.id,
        getUsers: (state) => state.users,
        getName: (state) => state.name,
        getTeam: (state) => state.team,
        getRedactPayload: (state) => state.redactPayload,
        getDashboardVersion: (state) => state.dashboardVersion,
        getApiRuntimeVersion: (state) => state.apiRuntimeVersion,
        getUrlRegexMatchingEnabled: (state) => state.urlRegexMatchingEnabled,
        getSetupType: (state) => state.setupType,
        getLastLoginTs: (state) => state.lastLoginTs,
        getPrivateCidrList: (state) => state.privateCidrList,
        getEnableDebugLogs: (state) => state.enableDebugLogs,
        getFilterHeaderValueMap: (state) => state.filterHeaderValueMap,
        getApiCollectionNameMapper: (state) => state.apiCollectionNameMapper
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
                state.urlRegexMatchingEnabled = false
                state.dashboardVersion = "-"
                state.setupType = "PROD"
                state.mergeAsyncOutside = false
                state.enableDebugLogs = false
                state.filterHeaderValueMap = null
                state.apiCollectionNameMapper = null
            } else {
                state.redactPayload = resp.accountSettings.redactPayload ? resp.accountSettings.redactPayload : false
                state.apiRuntimeVersion = resp.accountSettings.apiRuntimeVersion ? resp.accountSettings.apiRuntimeVersion : "-"
                state.urlRegexMatchingEnabled = resp.accountSettings.urlRegexMatchingEnabled
                state.dashboardVersion = resp.accountSettings.dashboardVersion ? resp.accountSettings.dashboardVersion : "-"
                state.redactPayload = resp.accountSettings.redactPayload ? resp.accountSettings.redactPayload : false
                state.setupType = resp.accountSettings.setupType
                state.mergeAsyncOutside = resp.accountSettings.mergeAsyncOutside || false
                state.privateCidrList = resp.accountSettings.privateCidrList
                state.enableDebugLogs = resp.accountSettings.enableDebugLogs
                state.filterHeaderValueMap = resp.accountSettings.filterHeaderValueMap
                state.apiCollectionNameMapper = resp.accountSettings.apiCollectionNameMapper
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
        deleteApiCollectionNameMapper({commit, dispatch}, {regex}) {
            api.deleteApiCollectionNameMapper(regex).then((resp => {

                window._AKTO.$emit('SHOW_SNACKBAR', {
                    show: true,
                    text: "Collection replacement deleted successfully",
                    color: 'green'
                })

                api.fetchAdminSettings().then((resp => {
                    commit('SET_ADMIN_SETTINGS', resp)
                }))
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
        toggleDebugLogsFeature({commit, dispatch, state}, v) {
            api.toggleDebugLogsFeature(v).then((resp => {
                state.enableDebugLogs = v
            }))
        },
        updateMergeAsyncOutside({commit, dispatch, state}) {
            api.updateMergeAsyncOutside().then((resp => {
                state.mergeAsyncOutside = true
            }))
        },
        updateEnableNewMerge({commit, dispatch, state}, v) {
            api.toggleNewMergingEnabled(v).then((resp => {
                state.urlRegexMatchingEnabled = v
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
        addFilterHeaderValueMap({commit, dispatch}, {filterHeaderValueMapKey, filterHeaderValueMapValue}) {
            let filterHeaderValueMap = {};
            filterHeaderValueMap[filterHeaderValueMapKey] = filterHeaderValueMapValue
            return api.addFilterHeaderValueMap(filterHeaderValueMap).then(resp => {
                return resp
            })
        },
        addApiCollectionNameMapper({commit, dispatch}, {regex, newName, headerName}) {
            return api.addApiCollectionNameMapper(regex, newName, headerName).then(resp => {
                window._AKTO.$emit('SHOW_SNACKBAR', {
                    show: true,
                    text: "Collection replacement added successfully",
                    color: 'green'
                })

                api.fetchAdminSettings().then((resp => {
                    commit('SET_ADMIN_SETTINGS', resp)
                }))
            })
        },
        emptyState({commit, dispatch}) {
            commit('EMPTY_STATE')
        }
    }
}

export default team