<template>
      
      <api-token title="Slack updates"
        :burp_tokens="slack_webhooks"
        addTokenTitle="Add Slack webhook"
        avatar_image="$slack"
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
</template>

<script>

import ApiToken from "./ApiToken.vue"
import SimpleTextField from '@/apps/dashboard/shared/components/SimpleTextField'
import api from "../../api.js"

export default {
    name: "SlackIntegration",
    components: {
      ApiToken,
      SimpleTextField
    },
    data () {
      return {
        slack_webhooks: []
      }
    },
    methods: {
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
          case "SLACK":
            this.slack_webhooks.push(x)
            break;
        }
      })
      this.slack_webhooks = [...this.slack_webhooks]
    }
}
</script>

<style>

</style>