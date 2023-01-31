<template>
  <div @click="loginViaGoogle">
    <slot/>
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
      let result = await request({
        url: 'api/googleConfig',
        method: 'post',
        data: {}
      })
      if (this.purpose === 'addfile') {
        var objThis = this
        gapi.load('auth2', function() {
          objThis.auth2 = gapi.auth2.init({
            client_id: result.client_id,
            scope: objThis.scope
          });
          objThis.auth2.grantOfflineAccess().then((x) => {
            objThis.$emit('signInCallback', x)
          }).catch(() => {});
        })
      } else {
        window.location.href = 'https://accounts.google.com/o/oauth2/v2/auth?' +
            'access_type=offline&' +
            'scope=https%3A//www.googleapis.com/auth/userinfo.email%20https%3A//www.googleapis.com/auth/userinfo.profile&' +
            'response_type=code&' +
            'state=' + this.purpose + '&' +
            'redirect_uri=https%3A//staging.akto.io%3A8443/signup-google&' +
            'client_id=' + result.client_id
      }
    }
  },
}
</script>

<style scoped>

</style>