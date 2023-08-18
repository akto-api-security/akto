import Vue from 'vue'
import Vuex from 'vuex'
import endpoints from './endpoints'

Vue.use(Vuex)

const store = new Vuex.Store({
    namespaced: true,
    modules: {
        endpoints
    }
})

export default store