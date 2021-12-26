import Vue from 'vue'
import Router from 'vue-router'
const PageLogin  = () => import( '@/apps/login/App')
const PageDashboard  = () => import( '@/apps/dashboard/App')
const PageToday  = () => import( "@/apps/dashboard/views/today/Today")
import store from '@/apps/main/store/module'
const PageSignup = () => import("@/apps/signup/PageSignup")
const PageOnboard = () => import("@/apps/signup/PageOnboard")
const PageSettings = () => import("@/apps/dashboard/views/settings/PageSettings")
const Observe = () => import( "@/apps/dashboard/views/observe/inventory/Observe")
const Inventory = () => import("@/apps/dashboard/views/observe/inventory/Inventory")
const APIParameters = () => import("@/apps/dashboard/views/observe/inventory/components/APIParameters")
const APIEndpoints = () => import("@/apps/dashboard/views/observe/inventory/components/APIEndpoints")
const APICollections = () => import("@/apps/dashboard/views/observe/collections/APICollections")
const SensitiveData = () => import("@/apps/dashboard/views/observe/inventory/SensitiveData")
const ApiChanges = () => import("@/apps/dashboard/views/observe/inventory/Changes")
Vue.use(Router)

function getId (route) {
    return  {
        id: +route.params.id
    }
}
const router =  new Router({
    mode: 'history',
    base: process.env.BASE_URL,
    routes: [
        {
            path: '/',
            redirect: 'login',
            component: PageLogin
        },
        {
            path: '/login',
            name: 'login',
            component: PageLogin
        },
        {
            path: '/signup',
            name: 'signup',
            component: PageSignup
        },
        {
            path: '/setup',
            name: 'setup',
            component: PageOnboard
        },
        {
            path: '/dashboard',
            name: 'dashboard',
            component: PageDashboard,
            redirect: '/dashboard/testing',
            children: [
                {
                    path: 'testing',
                    name: 'testing',
                    components: {
                        default: PageToday
                    }
                },
                {
                    path: 'settings',
                    name: 'settings',
                    components: {
                        default: PageSettings
                    }
                },
                {
                    path: 'observe',
                    name: 'observe',
                    component: Observe,
                    children:[
                        {
                            path: 'inventory',
                            name: 'inventory',
                            component: Inventory,
                            children: [
                                {
                                    path:'',
                                    name:'default',
                                    component: APICollections        
                                },
                                {
                                    path:':apiCollectionId',
                                    name:'apiCollection',
                                    component: APIEndpoints        
                                },
                                {
                                    path:':apiCollectionId/:urlAndMethod',
                                    name:'apiCollection/urlAndMethod',
                                    component: APIParameters,
                                    props: route => ({
                                        urlAndMethod: atob(route.params.urlAndMethod)
                                    })
                                }        
                            ]
                        },                        
                        {
                            path: 'changes',
                            name: 'changes',
                            component: ApiChanges
                        },
                        {
                            path: 'sensitive',
                            name: 'sensitive',
                            component: SensitiveData
                        }        
                    ]
                }
            ]
        },
    ]
})


router.beforeEach((to, from, next) => {
    if (window._AKTO) {
        window._AKTO.$emit('HIDE_SNACKBAR')
    }

    if (to.name === 'signup' || to.name === 'login') {
        store.commit('auth/SET_ACCESS_TOKEN',null)
    }
    if (window.mixpanel)
        window.mixpanel.track(to.name)
    next()
})

export default router