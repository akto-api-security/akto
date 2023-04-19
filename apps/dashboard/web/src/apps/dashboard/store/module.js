import Vue from 'vue'
import Vuex from 'vuex'
import api from '../api'

Vue.use(Vuex)

const state = {
    loadingSnackBars: [
    ],
}


const dashboard = {
    namespaced: true,
    state: state,
    mutations: {
        UPDATE_LOADING_SNACKBARS(state, loaderList) {
            let val = []
            loaderList.forEach((x) => {
                val.push(
                    {
                        title: x['type']['title'],
                        subTitle:x['type']['subTitle'],
                        percentage:x['percentage'],
                        id: x["id"],
                        hexId: x["hexId"]
                    }
                )
            })
            state.loadingSnackBars = val
        }
    },
    actions: {
        fetchActiveLoaders({commit}) {
            api.fetchActiveLoaders().then((resp) => {
                let loaderList = resp['loaderList']
                commit('UPDATE_LOADING_SNACKBARS', loaderList)
            })
        },
        closeLoader({commit}, hexId) {
            state.loadingSnackBars = state.loadingSnackBars.filter((x) => {
                return x['hexId'] !== hexId
            })
            api.closeLoader(hexId)
        }
    }
}


export default dashboard