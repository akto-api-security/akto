import Vue from 'vue'
import Vuex from 'vuex'
import auth from '@/apps/login/store/modules/auth/module'
import team from '@/apps/dashboard/views/settings/store/module'
import today from '@/apps/dashboard/views/today/store/module'
import testing from '@/apps/dashboard/views/testing/store/module'
import inventory from '@/apps/dashboard/views/observe/inventory/store/module'
import changes from '@/apps/dashboard/views/observe/changes/store/module'
import sensitive from '@/apps/dashboard/views/observe/sensitive/store/module'
import collections from '@/apps/dashboard/views/observe/collections/store/module'
import data_types from '@/apps/dashboard/views/settings/components/data_types/store/module'
import tag_configs from '@/apps/dashboard/views/settings/components/tag_configs/store/module'
import auth_types from '../../dashboard/views/settings/components/auth_types/store/module'
import issues from '@/apps/dashboard/views/issues/store/module'
import test_roles from '@/apps/dashboard/views/testing/components/test_roles/store/module'
import onboarding from '@/apps/dashboard/views/onboarding/store/module'
import dashboard from '@/apps/dashboard/store/module'

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
        sensitive,
        data_types,
        tag_configs,
        auth_types,
        testing,
        issues,
        test_roles,
        onboarding,
        dashboard
    },
    state:{
        globalUid:-1
    }
})

export default store