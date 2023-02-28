import Vue from 'vue'
import Vuex from 'vuex'
import api from '../api'


Vue.use(Vuex)

const state = {
    loading: false,
    selectedCollection: null
}

const onboarding = {
    namespaced: true,
    state: state,
    mutations: {
        SELECT_COLLECTION (state, selectedCollection) {
            state.selectedCollection = selectedCollection
        },
    },
    actions: {
        collectionSelected({commit}, selectedCollection) {
            console.log(selectedCollection);
            commit('SELECT_COLLECTION', selectedCollection)
        },
    },
    getters: {
    }
}

export default onboarding