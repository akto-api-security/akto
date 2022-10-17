import Vue from 'vue'
import Router from 'vue-router'
const PageLogin  = () => import( '@/apps/login/App')
const PageDashboard  = () => import( '@/apps/dashboard/App')
const PageToday  = () => import( "@/apps/dashboard/views/today/Today")
const PageTesting  = () => import( "@/apps/dashboard/views/testing/PageTesting")
const TestingRunsTable  = () => import( "@/apps/dashboard/views/testing/components/TestingRunsTable")
const TestingRunResults  = () => import( "@/apps/dashboard/views/testing/components/TestingRunResults")
const CreateTestingRun  = () => import( "@/apps/dashboard/views/testing/components/CreateTestingRun")
import store from '@/apps/main/store/module'
const PageSignup = () => import("@/apps/signup/PageSignup")
const PageOnboard = () => import("@/apps/signup/PageOnboard")
const PageSettings = () => import("@/apps/dashboard/views/settings/PageSettings")
const Observe = () => import( "@/apps/dashboard/views/observe/inventory/Observe")
const Inventory = () => import("@/apps/dashboard/views/observe/inventory/Inventory")
const APIParameters = () => import("@/apps/dashboard/views/observe/inventory/components/APIParameters")
const APIEndpoints = () => import("@/apps/dashboard/views/observe/inventory/components/APIEndpoints")
const APICollections = () => import("@/apps/dashboard/views/observe/collections/APICollections")
const SensitiveData = () => import("@/apps/dashboard/views/observe/sensitive/SensitiveData")
const ApiChanges = () => import("@/apps/dashboard/views/observe/changes/Changes")
const ParamState = () => import("@/apps/dashboard/views/observe/misc/ParamState")

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
            path: '/dashboard/setup',
            name: 'setup',
            component: PageOnboard
        },
        {
            path: '/dashboard',
            name: 'dashboard',
            component: PageDashboard,
            redirect: '/dashboard/testing',
            beforeEnter (to, from, next) {
                store.dispatch('collections/loadAllApiCollections').then(() => next()).catch(() => next())
            },
            children: [
                {
                    path: 'testing',
                    name: 'testing',
                    redirect: 'testing/active',
                    components: {
                        default: PageTesting
                    },
                    children: [
                        {
                            path: 'active',
                            name: 'testResults',
                            component: TestingRunsTable,
                            props: route => ({
                                active: true
                            })
                        },
                        {
                            path: 'completed',
                            name: 'testResults',
                            component: TestingRunsTable,
                            props: route => ({
                                active: false
                            })
                        },
                        {
                            path: 'inactive',
                            name: 'inactiveTestResults',
                            component: TestingRunsTable,
                            props: route => ({
                                active: false
                            })
                        },
                        {
                            path: ':testingRunHexId/results',
                            name: 'testResults',
                            component: TestingRunResults,
                            props: route => ({
                                testingRunHexId: route.params.testingRunHexId,
                                key: route.params.testingRunHexId
                            })
                        },
                        {
                            path: 'create/:apiCollectionId',
                            name: 'createFromApiCollection',
                            component: CreateTestingRun

                            
                        }                        
                    ]
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
                                    component: APIEndpoints,
                                    props: route => ({
                                        apiCollectionId: +route.params.apiCollectionId
                                    })
                                },
                                {
                                    path:':apiCollectionId/:urlAndMethod',
                                    name:'apiCollection/urlAndMethod',
                                    component: APIParameters,
                                    props: route => ({
                                        urlAndMethod: atob(route.params.urlAndMethod),
                                        apiCollectionId: +route.params.apiCollectionId
                                    })
                                }        
                            ]
                        },                        
                        {
                            path: 'changes',
                            name: 'changes',
                            component: ApiChanges,
                            props: route => ({
                                openTab: route.query.tab === "parameters" ? "parameters" : "endpoints",
                                defaultStartTimestamp: +route.query.start,
                                defaultEndTimestamp: +route.query.end
                            })
                        },
                        {
                            path: 'sensitive',
                            name: 'sensitive',
                            component: SensitiveData
                        },
                        {
                            path: 'param_state',
                            name: 'param_state',
                            component: ParamState,
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
    if (window.mixpanel && window.mixpanel.track)
        window.mixpanel.track(to.name)
    next()
})

export default router