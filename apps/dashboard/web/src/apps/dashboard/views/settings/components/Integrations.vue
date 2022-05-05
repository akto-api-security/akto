<template>
    <div class="pa-4">
      <postman/>
      <v-divider/>

      <api-token
          title="Burp" 
          :burp_tokens="burp_tokens"
          avatar_image="burpsuite.svg"
          @generateToken="addBurpToken"
          @deleteToken="deleteBurpToken"
          />
      <v-divider/>
      
      <api-token title="External APIs"
        :burp_tokens="external_api_tokens"
        avatar_image="rest_api.svg"
        @generateToken="addExternalApiToken"
        @deleteToken="deleteExternalApiToken"/>
      <v-divider/>
      
      <api-token title="Slack updates"
        :burp_tokens="slack_webhooks"
        addTokenTitle="Add Slack webhook"
        avatar_image="logo_slack.svg"
        @generateToken="addSlackWebhook"
        @deleteToken="deleteSlackWebhook">

        <template slot="default">
          <simple-text-field 
              :readOutsideClick="true"
              placeholder="Add Slack webhook url"
              @changed="addSlackWebhook"
          />          
        </template>

      </api-token>
    </div>

</template>

<script>

import Postman from "./integrations/Postman.vue"
import ApiToken from "./integrations/ApiToken.vue"
import SimpleTextField from '@/apps/dashboard/shared/components/SimpleTextField'
import api from "../api.js"
import func from "@/util/func"

export default {
    name: "Integrations",
    components: {
      Postman,
      ApiToken,
      SimpleTextField
    },
    data () {
      return {
        burp_tokens: [],
        external_api_tokens: [],
        slack_webhooks: []
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
      },
      addSlackWebhook(webhookUrl) {
        api.addSlackWebhook(webhookUrl).then(resp => {
          if (resp.error) {
              window._AKTO.$emit('SHOW_SNACKBAR', {
                  show: true,
                  text: resp.error,
                  color: 'red'
              })
          } else {
            this.slack_webhooks.push({
              id: resp.apiTokenId,
              key: resp.webhookUrl,
              timestamp: resp.apiTokenId
            })
            this.slack_webhooks = [...this.slack_webhooks]
            window._AKTO.$emit('SHOW_SNACKBAR', {
                show: true,
                text: "Slack webhook added successfully",
                color: 'green'
            })

          }
        })
      },
      deleteSlackWebhook(webhookId) {
        api.deleteSlackWebhook(webhookId).then(resp => {
          window._AKTO.$emit('SHOW_SNACKBAR', {
              show: true,
              text: "Slack webhook deleted successfully",
              color: 'green'
          })
          this.slack_webhooks = this.slack_webhooks.filter(e => e.id !== webhookId)
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

          case "EXTERNAL_API":
            this.external_api_tokens.push(x)
            break;

          case "SLACK":
            this.slack_webhooks.push(x)
            break;
        }
      })
      this.burp_tokens = [...this.burp_tokens]
      
      this.external_api_tokens = [...this.external_api_tokens]    

      this.slack_webhooks = [...this.slack_webhooks]
    },

}
</script>

<style>

</style>