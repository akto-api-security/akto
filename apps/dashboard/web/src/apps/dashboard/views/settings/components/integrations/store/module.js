import Vue from 'vue'
import Vuex from 'vuex'
import api from '../api'

Vue.use(Vuex)

const state = {
    loading: false,
    selectedWebhook: {},
    webhooks: [],
    webhookName: "",
    url: "",
    queryParams: "",
    method: "POST",
    headerString: "{'content-type': 'application/json'}", 
    body: "{}", 
    frequencyInSeconds: 86400,
    selectedWebhookOptions: [],
    newEndpointConditions: {},
    newSensitiveEndpointConditions: {}
}

const integration = {
    namespaced: true,
    state: state,
    mutations: {
        EMPTY_STATE(state) {
            state.loading = false
            state.selectedWebhook = {}
            state.webhooks = []
            state.webhookName = ""
            state.url = ""
            state.queryParams = ""
            state.method = "POST"
            state.headerString = "{'content-type': 'application/json'}"
            state.body = "{}"
            state.frequencyInSeconds = 86400
            state.selectedWebhookOptions = []
            state.newEndpointConditions = {}
            state.newSensitiveEndpointConditions = {}
        },
        SAVE_WEBHOOKS(state, { webhooks }) {
            state.webhooks = webhooks
        },
        SAVE_SELECTED_WEBHOOK(state, { selectedWebhook }) {
            state.selectedWebhook = selectedWebhook
            state.webhookName = selectedWebhook.webhookName
            state.url = selectedWebhook.url
            state.queryParams = selectedWebhook.queryParams
            state.method = selectedWebhook.method
            state.headerString = selectedWebhook.headerString
            state.body = selectedWebhook.body
            state.frequencyInSeconds = selectedWebhook.frequencyInSeconds
            state.selectedWebhookOptions = selectedWebhook.selectedWebhookOptions
            state.newEndpointConditions = selectedWebhook.newEndpointConditions
            state.newSensitiveEndpointConditions = selectedWebhook.newSensitiveEndpointConditions
        }
    },
    actions: {
        async addCustomWebhook({ commit }, { webhookName, url, queryParams, method, headerString, body, frequencyInSeconds, selectedWebhookOptions, newEndpointConditions, newSensitiveEndpointConditions }) {
            state.loading = true
            await api.addCustomWebhook(webhookName, url, queryParams, method, headerString, body,
                 frequencyInSeconds, selectedWebhookOptions, newEndpointConditions, newSensitiveEndpointConditions).then((response) => {
                state.loading = false
            })
        },
        async updateCustomWebhook({ commit }, { id, webhookName, url, queryParams, method, headerString, body, frequencyInSeconds, selectedWebhookOptions, newEndpointConditions, newSensitiveEndpointConditions }) {
            state.loading = true
            await api.updateCustomWebhook(id, webhookName, url, queryParams, method, headerString, body, frequencyInSeconds, selectedWebhookOptions, newEndpointConditions, newSensitiveEndpointConditions).then((response) => {
                state.loading = false
            })
        },
        async fetchCustomWebhooks({ commit }) {
            state.loading = true
            commit('EMPTY_STATE')
            await api.fetchCustomWebhooks().then((response) => {
                state.loading = false
                commit('SAVE_WEBHOOKS', response)
            })
        }
        
    }
}

export default integration