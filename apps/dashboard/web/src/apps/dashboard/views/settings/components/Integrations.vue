<template>
    <div class="pa-4">
      <postman/>
      <v-divider></v-divider>
      <api-token
          title="Burp" 
          :burp_tokens="burp_tokens"
          avatar_image="burpsuite.svg"
          @generateToken="addBurpToken"
          @deleteToken="deleteBurpToken"
          />
      <v-divider></v-divider>
      <api-token title="External APIs"
        :burp_tokens="external_api_tokens"
        avatar_image="rest_api.svg"
        @generateToken="addExternalApiToken"
        @deleteToken="deleteExternalApiToken"/>


    </div>

</template>

<script>

import Postman from "./integrations/Postman.vue"
import ApiToken from "./integrations/ApiToken.vue"
import api from "../api.js"

export default {
    name: "Integrations",
    components: {
      Postman,ApiToken
    },
    data () {
      return {
        burp_tokens: [],
        external_api_tokens: []
      }
    },
    methods: {
      addBurpToken() {
        api.addBurpToken().then((resp) => {
          this.burp_tokens.push(...resp.apiTokenList)
          this.burp_tokens = [...this.burp_tokens]
        })
      },
      addExternalApiToken() {
        api.addExternalApiToken().then((resp) => {
          this.external_api_tokens.push(...resp.apiTokenList)
          this.external_api_tokens = [...this.external_api_tokens]
        })
      },
      deleteBurpToken(id) {
        api.deleteApiToken(id).then((resp) => {
          if (resp.apiTokenDeleted) {
            this.burp_tokens = this.burp_tokens.filter(function(el) { return el.id != id; })
          }
        })
      },
      deleteExternalApiToken(id) {
        api.deleteApiToken(id).then((resp) => {
          if (resp.apiTokenDeleted) {
            this.external_api_tokens = this.external_api_tokens.filter(function(el) { return el.id != id; })
          }
        })
      }
    },
    async mounted() {
      let resp = await api.fetchApiTokens()
      resp.apiTokenList.forEach(x => {
        if (x.utility === "BURP") {
          this.burp_tokens.push(x)
          
        } else if (x.utility === "EXTERNAL_API") {
          this.external_api_tokens.push(x)
        }
      })
      this.burp_tokens = [...this.burp_tokens]
      
      this.external_api_tokens = [...this.external_api_tokens]    
    },

}
</script>

<style>

</style>