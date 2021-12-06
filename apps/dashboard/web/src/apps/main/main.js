import Vue from 'vue'
import App from './App.vue'
import vuetify from '@/plugins/vuetify'
import router from './router'
import store from './store/module'

// Storing the access token from login.jsp
store.state.auth.access_token = window.ACCESS_TOKEN;

new Vue({
  el: '#app',
  vuetify,
  router,
  store,
  render: h => h(App)
})
