import Vue from 'vue'
import Vuex from 'vuex'
import api from '../api'


Vue.use(Vuex)

const team = {
    namespaced: true,
    state: {
        id: null,
        users: {},
        fetchTs: 0,
        details: {}
    },
    getters: {
        getId: (state) => state.id,
        getUsers: (state) => state.users,
        getName: (state) => state.name,
        getDetails: (state) => state.details
    },
    mutations: {
        SET_TEAM_DETAILS(state, details) {
            state.id = details.id
            state.users = details.users
            state.name = details.name
            state.fetchTs = parseInt(new Date().getTime()/1000)
            state.details = details.team
        },
        EMPTY_STATE(state) {
            state.id = null
            state.users = {}
            state.name = ''
            state.fetchTs = 0
            state.details = {}
        }
    },
    actions: {
        getTeamData({commit, dispatch}, id) {
            return api.getTeamData(id).then((resp) => {
                commit('SET_TEAM_DETAILS', resp)
            })
        },
        emptyState({commit, dispatch}) {
            commit('EMPTY_STATE')
        }
    }
}

export default team