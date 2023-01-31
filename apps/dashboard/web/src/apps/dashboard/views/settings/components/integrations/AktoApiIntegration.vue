<template>
      <api-token title="External APIs"
        :burp_tokens="external_api_tokens"
        avatar_image="$restapi"
        @generateToken="addExternalApiToken"
        @deleteToken="deleteExternalApiToken"/>
</template>

<script>

import ApiToken from "./ApiToken.vue"
import api from "../../api.js"

export default {
    name: "AktoApiIntegration",
    components: {
      ApiToken
    },
    data () {
      return {
        external_api_tokens: []
      }
    },
    methods: {
      addExternalApiToken() {
        api.addExternalApiToken().then((resp) => {
          this.external_api_tokens.push(...resp.apiTokenList)
          this.external_api_tokens = [...this.external_api_tokens]
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
        switch (x.utility) {
          case "EXTERNAL_API":
            this.external_api_tokens.push(x)
            break;
        }
      })
      this.external_api_tokens = [...this.external_api_tokens]    

    }

}
</script>

<style>

</style>