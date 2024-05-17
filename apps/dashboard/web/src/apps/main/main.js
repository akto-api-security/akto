import Vue from 'vue'
import App from './App.vue'
import ExpiredApp from './ExpiredApp.vue'
import vuetify from '@/plugins/vuetify'
import router from './router'
import singleRouter from "./router/singlePageIndex"
import store from './store/module'

import { VuePlugin } from "vuera";
Vue.use(VuePlugin);

// Storing the access token from login.jsp
store.state.auth.access_token = window.ACCESS_TOKEN;

let expired = false;

if (
  window.STIGG_CUSTOMER_ID &&
  (window.EXPIRED && window.EXPIRED == 'true')) {

  expired = true;
}

if(expired){
  new Vue({
    el: '#app',
    vuetify,
    singleRouter,
    render: h => h(ExpiredApp)
  })
} else {
  new Vue({
    el: '#app',
    vuetify,
    router,
    store,
    render: h => h(App)
  })
}
