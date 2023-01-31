import Vue from 'vue'
import Vuex from 'vuex'
import api from '../api'


Vue.use(Vuex)

var state = {
    loading: false,
    fetchTs: 0,
    content: null,
    errors: null,
    auditId: null,
    contentId: null,
    endpoint: '',
    description: '',
    numberOfPaths: 0,
    authProtocol: null,
    filename: '',
    testDetails: null,
    testResults: null
}

const today = {
    namespaced: true,
    state: state,
    mutations: {
        EMPTY_STATE (state) {
            state.loading = false
            state.fetchTs = 0
            content = null
            errors = null
            auditId = null
            contentId = null
            endpoint = ''
            description = ''
            numberOfPaths = 0
            authProtocol = null
            filename = ''
            testDetails = null
            testResults = null
        },
        SAVE_AUDIT_RESULTS (state, {auditId, contentId, errors, endpoint, description, numberOfPaths, authProtocol}) {
            state.auditId = auditId
            state.contentId = contentId
            state.errors = errors
            state.endpoint = endpoint
            state.description = description
            state.numberOfPaths = numberOfPaths
            state.authProtocol = authProtocol
        },
        SAVE_TEST_DETAILS (state, testDetails) {
            state.testDetails = testDetails
        },
        SAVE_TEST_RESULTS (state, attempts) {
            state.testResults = attempts
        }
    },
    actions: {
        saveContentAndFetchAuditResults({ commit, dispatch, state }, {content, filename}) {
            state.loading = true
            api.saveContentAndFetchAuditResults({content, filename}).then(resp => {
                let {content, filename} = resp.data
                state.filename = filename
                state.content = content
                let endpoint = content.servers[0].url
                let description = content.info.description
                let numberOfPaths = Object.keys(content.paths|| {}).length
                let authProtocol = content.components.securitySchemes

                commit('SAVE_AUDIT_RESULTS', {...resp.data, description, endpoint, numberOfPaths, authProtocol })
                state.loading = false
            }).catch((err) => {
                state.loading = false
            })
        },
        fetchAuditResults({ commit, dispatch, state }) {
            state.loading = true
            api.fetchAuditResults().then(resp => {
                let {content, filename} = resp.data

                if (content) {
                    state.filename = filename
                    state.content = content
                    let endpoint = content.servers[0].url
                    let description = content.info.description
                    let numberOfPaths = Object.keys(content.paths|| {}).length
                    let authProtocol = content.components.securitySchemes
        
                    commit('SAVE_AUDIT_RESULTS', {...resp.data, description, endpoint, numberOfPaths, authProtocol })
                }
                state.loading = false
            }).catch((err) => {
                state.loading = false
            })
        },
        saveTestConfig({commit, state}, {authProtocolSettings, testEnvSettings, authProtocolSpecs}) {
            state.loading = true
            api.saveTestConfig({authProtocolSettings, testEnvSettings, authProtocolSpecs, contentId: state.contentId}).then(resp => {
                commit('SAVE_TEST_DETAILS', resp.data)
                state.loading = false
            }).catch( (e) => {
                state.loading = false
            })
        },
        fetchTestResults({commit, state}) {
            api.fetchTestResults({attemptIds: state.testDetails.attempts}).then(resp => {
                commit('SAVE_TEST_RESULTS', resp.data)
            }).catch((e) => {
            })
        },
        emptyState({commit, dispatch}, payload, options) {
            commit('EMPTY_STATE', payload, options)
        }
    },
    getters: {
        getFetchTs: (state) => state.fetchTs,
        getLoading: (state) => state.loading,
        getContent: (state) => state.content,
        getErrors:  (state) => state.errors,
        getContentId: (state) => state.contentId,
        getAuditId: (state) => state.auditId
    }
}

export default today