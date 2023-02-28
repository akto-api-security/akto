import Vue from 'vue'
import Vuex from 'vuex'
import api from '../api'


Vue.use(Vuex)

const state = {
    loading: false,
    selectedCollection: null,
    selectedTestSuite: null
}

const onboarding = {
    namespaced: true,
    state: state,
    mutations: {
        SELECT_COLLECTION (state, selectedCollection) {
            state.selectedCollection = selectedCollection
        },
        SELECT_TEST_SUITE(state, selectedTestSuite) {
            state.selectedTestSuite = selectedTestSuite
        }
    },
    actions: {
        collectionSelected({commit}, selectedCollection) {
            commit('SELECT_COLLECTION', selectedCollection)
        },
        testSuiteSelected({commit}, selectedTestSuite) {
            commit('SELECT_TEST_SUITE', selectedTestSuite)
        },
    },
    getters: {
    }
}

export default onboarding