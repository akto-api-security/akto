<template>
  <div @click="loginViaGoogle">
    <slot />
  </div>
</template>

<script>

import obj from "@/util/obj";
import request from "@/util/request";

export default {
  name: "GoogleAuth",
  props: {
    scope: obj.strR,
    purpose: {
      type: String,
      default: "signup"
    }
  },
  data() {
    return {
      loading: false
    }
  },
  methods: {
    async loginViaGoogle() {
      window.location.href = 'https://accounts.google.com/o/oauth2/v2/auth?' +
        'access_type=offline&' +
        'scope=https%3A//www.googleapis.com/auth/userinfo.email%20https%3A//www.googleapis.com/auth/userinfo.profile&' +
        'response_type=code&' +
        'state=' + this.purpose + '&' +
        'redirect_uri=http%3A//' + window.location.host + '/signup-google&' +
        'client_id=779574722609-u7r6qf8ra7b46pdb80a4vp9neipf16mr.apps.googleusercontent.com'
    }
  }
}
</script>

<style scoped></style>