import Vue from 'vue'
import Vuex from 'vuex'
import auth from '@/apps/login/store/modules/auth/module'
import team from '@/apps/dashboard/views/settings/store/module'
import today from '@/apps/dashboard/views/today/store/module'
import inventory from '@/apps/dashboard/views/observe/inventory/store/module'
import changes from '@/apps/dashboard/views/observe/changes/store/module'
import sensitive from '@/apps/dashboard/views/observe/sensitive/store/module'
import collections from '@/apps/dashboard/views/observe/collections/store/module'

Vue.use(Vuex)

const store = new Vuex.Store({
    namespaced: true,
    modules: {
        auth,
        team,
        today,
        inventory,
        collections,
        changes,
        sensitive
    }
})

export default store