<template>
      <api-token
          title="Burp" 
          :burp_tokens="burp_tokens"
          avatar_image="$burpsuite"
          @generateToken="addBurpToken"
          @deleteToken="deleteBurpToken"
      />
</template>

<script>

import ApiToken from "./ApiToken.vue"
import api from "../../api.js"

export default {
    name: "BurpSuiteIntegration",
    components: {
      ApiToken
    },
    data () {
      return {
        burp_tokens: []
      }
    },
    methods: {
      addBurpToken() {
        api.addBurpToken().then((resp) => {
          this.burp_tokens.push(...resp.apiTokenList)
          this.burp_tokens = [...this.burp_tokens]
        })
      },
      deleteBurpToken(id) {
        api.deleteApiToken(id).then((resp) => {
          if (resp.apiTokenDeleted) {
            this.burp_tokens = this.burp_tokens.filter(function(el) { return el.id != id; })
          }
        })
      }
    },
    async mounted() {
      let resp = await api.fetchApiTokens()
      resp.apiTokenList.forEach(x => {
        switch (x.utility) {
          case "BURP": 
            this.burp_tokens.push(x)
            break;
        }
      })
      this.burp_tokens = [...this.burp_tokens]
      
    }

}
</script>

<style>

</style>