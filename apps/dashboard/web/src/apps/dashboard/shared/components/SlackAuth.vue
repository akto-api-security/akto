<template>
  <div @click="loginViaSlack">
    <slot/>
  </div>
</template>

<script>

import obj from "@/util/obj";

export default {
  name: "SlackAuth",
  props: {
    loginOnly: obj.boolR,
    sign: {
      type: String,
      default: "up"
    }
  },
  data() {
    return {
      loading: false
    }
  },
  methods: {
    loginViaSlack() {
      var scopes =
          this.loginOnly ?
              ['identity.basic','identity.email','identity.team','identity.avatar'] :
              ['chat:write', 'im:read', 'im:write', 'groups:read', 'groups:write', 'channels:read','channels:write','users:read', 'users:read.email']

      var str = 'https://slack.com/oauth/v2/authorize?user_scope='+scopes.join(',')+'&client_id=1966181353905.1950504597541&state='+this.sign
      window.location.href = str
    }
  }
}
</script>

<style scoped>

</style>