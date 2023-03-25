<template>
      <api-token :title="title"
        :burp_tokens="external_api_tokens"
        :avatar_image="avatar_image"
        @generateToken="addApiToken"
        @deleteToken="deleteApiToken"/>
</template>

<script>

import ApiToken from "./ApiToken.vue"
import api from "../../api.js"
import obj from "@/util/obj"

export default {
    name: "AktoApiIntegration",
    props:{
      title: obj.strR,
      avatar_image: obj.strR,
      tokenOrigin: obj.strR
    },
    components: {
      ApiToken
    },
    data () {
      return {
        external_api_tokens: []
      }
    },
    methods: {
      addApiToken() {
        api.addApiToken(this.tokenOrigin).then((resp) => {
          this.external_api_tokens.push(...resp.apiTokenList)
          this.external_api_tokens = [...this.external_api_tokens]
        })
      },
      deleteApiToken(id) {
        api.deleteApiToken(id).then((resp) => {
          if (resp.apiTokenDeleted) {
            this.external_api_tokens = this.external_api_tokens.filter(function(el) { return el.id != id; })
          }
        })
      }
    },
    async mounted() {
      let resp = await api.fetchApiTokens()
      let type = "";
      switch(this.tokenOrigin){
        case "external_key": type = "EXTERNAL_API" 
          break;
        case "burp_key": type = "BURP"
          break;
        case "cicd_key": type = "CICD"
      }
      resp.apiTokenList.forEach(x => {
        switch (x.utility) {
          case type:
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