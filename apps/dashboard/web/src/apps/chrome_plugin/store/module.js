import Vue from 'vue'
import Vuex from 'vuex'
import inventory from '@/apps/dashboard/views/observe/inventory/store/module'

Vue.use(Vuex)

const store = new Vuex.Store({
    namespaced: true,
    modules: {
        inventory
    }
})

export default store