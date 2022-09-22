<template>
  <div>
    <div class="d-flex jc-end ma-2">
        <v-btn v-if="!showWebhookBuilder" primary dark color="#6200EA" @click="() => {originalStateFromDb = null; showWebhookBuilder = true}">
            Create new webhook
        </v-btn>
    </div>
    <simple-table 
        :headers="webhooksHeaders" 
        :items="webhooks" 
        name="Custom webhooks" 
        sortKeyDefault="createTime" 
        :sortDescDefault="true" 
        @rowClicked="openWebhookBuilder"
        :showName="true"
    />
    <v-dialog
        v-model="showWebhookBuilder"
        width="60%"
    >
        <div style="padding: 12px 24px 12px 24px; background: white">
          <div style="margin-bottom: 24px">
            <v-btn icon primary dark color="#6200EA" class="float-right" @click="() => { showWebhookBuilder = false; originalStateFromDb = null;}">
                <v-icon>$fas_times</v-icon>
            </v-btn>
          </div>
          <webhook-builder v-if="showWebhookBuilder" :originalStateFromDb="originalStateFromDb" @saveWebhook="saveWebhook" :loading="loading"/>
        </div>
    </v-dialog>

  </div>
</template>

<script>

import SimpleTable from "../../../../../shared/components/SimpleTable";
import WebhookBuilder from "./WebhookBuilder";
import api from "../../../api";

export default {
    name: "WebhookIntegration",
    components: {
      SimpleTable,
      WebhookBuilder
    },
    data () {
      return {
        showWebhookBuilder: false,
        originalStateFromDb: null,
        loading: false,
        webhooksHeaders: [
          {
              text: '',
              value: 'color'
          },
          {
              text: 'Name',
              value: 'webhookName'
          },
          {
              text: 'Create time',
              value: 'createTime'
          },
          {
              text: 'Status',
              value: 'activeStatus'
          }
        ],
        webhooks:[]
      }
    },
    methods: {
      openWebhookBuilder(item) {
        this.showWebhookBuilder = true
        this.originalStateFromDb = item
      },
      openWebhookResult() {
        this.showWebhookResult = true
      },
      fetchWebhooks() {
        api.fetchCustomWebhooks().then((resp) => {
          let customWebhooks = resp.customWebhooks;
          this.webhooks = customWebhooks.map((x) => {
            x["createTime"] = "2 mins ago"
            return x
          })
        })
      },
      saveWebhook(data) {
          this.loading=true
          let updatedData = data["updatedData"]

          let webhookName = updatedData["webhookName"]
          let url = updatedData["url"]
          let queryParams = updatedData["queryParams"]

          let method = updatedData["method"]
          method = this.validateMethod(method)
          if (!method) return // todo: throw banner error

          let headerString =  updatedData["headerString"] // todo:
          let frequencyInSeconds = updatedData["frequencyInSeconds"]

          let body = updatedData["body"]

          let createNew = data["createNew"]
          if (createNew) {
            api.addCustomWebhook(webhookName, url, queryParams, method, headerString, body, frequencyInSeconds)
          } else {
            let id = this.originalStateFromDb["id"]
            console.log(id);
            api.updateCustomWebhook(id, webhookName, url, queryParams, method, headerString, body, frequencyInSeconds)
          }
          this.loading=false
      },
      validateMethod(methodName) {
          let m = methodName.toUpperCase()
          let allowedMethods = ["GET", "POST", "PUT", "DELETE", "HEAD", "OPTIONS", "TRACE", "PATCH"]
          let idx = allowedMethods.indexOf(m);
          if (idx === -1) return null
          return allowedMethods[idx]
      }
    },
    async mounted() {
      this.fetchWebhooks()
    }
}
</script>

<style scoped lang="sass">


</style>