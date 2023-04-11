<template>
  <api-token :burp_tokens="burp_tokens" @generateToken="addBurpToken" @deleteToken="deleteBurpToken" />
</template>

<script>

import ApiToken from "../../settings/components/integrations/ApiToken.vue"
import api from "../api.js"
import func from '@/util/func'

export default {
  name: "BurpSuiteIntegration",
  components: {
    ApiToken
  },
  data() {
    return {
      burp_tokens: []
    }
  },
  methods: {
    addBurpToken() {
      api.addApiToken(func.testingResultType().BURP).then((resp) => {
        this.burp_tokens.push(...resp.apiTokenList)
        this.burp_tokens = [...this.burp_tokens]
      })
    },
    deleteBurpToken(id) {
      api.deleteApiToken(id).then((resp) => {
        if (resp.apiTokenDeleted) {
          this.burp_tokens = this.burp_tokens.filter(function (el) { return el.id != id; })
        }
      })
    }
  },
  async mounted() {
    let resp = await api.fetchApiTokens()
    resp.apiTokenList.forEach(x => {
      switch (x.utility) {
        case func.testingResultType().BURP:
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