<template>
</template>

<script>

import TextEditor from './TextEditor.vue';
import request from '@/util/request'

export default {
    name: 'WebsiteTestEditor',
  components: {
    TextEditor
  },
  data() {
    return {
        defaultTestId: 'REMOVE_TOKENS',
        isTokenAvailable: false
    }
  },
  computed: {
    // isAccessTokenAvailable() {
    //     debugger
    //     let isTokenAvailable = !(store.state.auth.access_token === undefined || store.state.auth.access_token === null)
    //     return isTokenAvailable
    // },
  },
  async mounted() {
    await request({
        url: '/tools/publicApi',
        method: 'GET'
    }).then((res) => {
        this.isTokenAvailable = true
        window.location.href = "/dashboard/test-editor/REMOVE_TOKENS"
    }).catch((e) => {
    })
  }
}
</script>
