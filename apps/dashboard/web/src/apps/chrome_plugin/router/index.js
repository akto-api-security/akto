import Vue from 'vue'
import Router from 'vue-router'
const PageIntroduction  = () => import( '@/apps/chrome_plugin/views/Introduction')
import store from '@/apps/main/store/module'
Vue.use(Router)

const router =  new Router({
    mode: 'history',
    base: '/src/pages/index.html',
    routes: [
        {
            path: '/index.html',
            redirect: '/'
        }, 
        {
            path: '/',
            component: PageIntroduction            
        }
    ]
})

export default router