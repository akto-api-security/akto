import Vue from 'vue'
import App from './App.vue'
import vuetify from '@/plugins/vuetify'
import router from './router'
import store from './store/module'

new Vue({
  el: '#app',
  vuetify,
  router,
  store,
  render: h => h(App)
})
