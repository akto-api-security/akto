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
        telemetryEnabled: null,
        setupType: null,
        lastLoginTs: null,
        privateCidrList: null,
        enableDebugLogs: null,
        filterHeaderValueMap: {},
        apiCollectionNameMapper: null,
        trafficAlertThresholdSeconds: null
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
        getTelemetryEnabled: (state) => state.telemetryEnabled,
        getSetupType: (state) => state.setupType,
        getLastLoginTs: (state) => state.lastLoginTs,
        getPrivateCidrList: (state) => state.privateCidrList,
        getEnableDebugLogs: (state) => state.enableDebugLogs,
        getFilterHeaderValueMap: (state) => state.filterHeaderValueMap,
        getApiCollectionNameMapper: (state) => state.apiCollectionNameMapper,
        getTrafficAlertThresholdSeconds: (state) => state.trafficAlertThresholdSeconds
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
                state.telemetryEnabled = false
                state.dashboardVersion = "-"
                state.setupType = "PROD"
                state.mergeAsyncOutside = false
                state.enableDebugLogs = false
                state.filterHeaderValueMap = {}
                state.apiCollectionNameMapper = null
                state.trafficAlertThresholdSeconds = 14400
            } else {
                state.redactPayload = resp.accountSettings.redactPayload ? resp.accountSettings.redactPayload : false
                state.apiRuntimeVersion = resp.accountSettings.apiRuntimeVersion ? resp.accountSettings.apiRuntimeVersion : "-"
                state.urlRegexMatchingEnabled = resp.accountSettings.urlRegexMatchingEnabled
                state.telemetryEnabled = resp.accountSettings.enableTelemetry ? resp.accountSettings.enableTelemetry : false
                state.dashboardVersion = resp.accountSettings.dashboardVersion ? resp.accountSettings.dashboardVersion : "-"
                state.redactPayload = resp.accountSettings.redactPayload ? resp.accountSettings.redactPayload : false
                state.setupType = resp.accountSettings.setupType
                state.mergeAsyncOutside = resp.accountSettings.mergeAsyncOutside || false
                state.privateCidrList = resp.accountSettings.privateCidrList
                state.enableDebugLogs = resp.accountSettings.enableDebugLogs
                state.filterHeaderValueMap = resp.accountSettings.filterHeaderValueMap ? resp.accountSettings.filterHeaderValueMap : {}
                state.apiCollectionNameMapper = resp.accountSettings.apiCollectionNameMapper
                state.trafficAlertThresholdSeconds = resp.accountSettings.trafficAlertThresholdSeconds || 14400
            }
        },
        SET_USER_INFO(state, resp) {
            state.lastLoginTs = resp.lastLoginTs
        },
        SET_FILTER_HEADER_VALUE_MAP(state, resp) {
            state.filterHeaderValueMap = resp.filterHeaderValueMap
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
        updateTelemetry({commit,dispatch, state}, v){
            api.toggleTelemetry(v).then((resp => {
                state.telemetryEnabled = v;
            }))
        }
        ,
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
        addFilterHeaderValueMap({commit, dispatch}, updatedMap) {
            return api.addFilterHeaderValueMap(updatedMap).then(resp => {
                commit('SET_FILTER_HEADER_VALUE_MAP', resp)
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
        makeAdmin({commit, dispatch}, user) {
            return api.makeAdmin(user.login).then(resp => {
                api.getTeamData().then((resp) => {
                    commit('SET_TEAM_DETAILS', resp)
                })
                return resp
            })
        },
        emptyState({commit, dispatch}) {
            commit('EMPTY_STATE')
        },
        updateTrafficAlertThresholdSeconds({commit, dispatch, state}, val) {
            api.updateTrafficAlertThresholdSeconds(val).then(resp => {
                state.trafficAlertThresholdSeconds = val
            })
        }
    }
}

export default team