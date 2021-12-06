import Vue from 'vue'
import Vuex from 'vuex'
import auth from '@/apps/login/store/modules/auth/module'
import team from '@/apps/dashboard/views/teams/store/module'
import today from '@/apps/dashboard/views/today/store/module'
import inventory from '@/apps/dashboard/views/observe/inventory/store/module'

Vue.use(Vuex)

const store = new Vuex.Store({
    namespaced: true,
    modules: {
        auth,
        team,
        today,
        inventory
    }
})

export default store