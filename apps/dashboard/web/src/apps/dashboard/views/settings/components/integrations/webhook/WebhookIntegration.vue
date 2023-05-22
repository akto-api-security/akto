<template>
  <div>
    <div class="d-flex jc-end ma-2">
        <v-btn v-if="!showWebhookBuilder" primary dark color="var(--themeColor)" @click="createNewWebhookFn">
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
        :actions="actions"
    />
    <v-dialog
        v-model="showWebhookBuilder"
        width="80%"
    >
        <div style="padding: 12px 24px 12px 24px; background: white">
          <div style="margin-bottom: 24px">
            <v-btn icon primary dark color="var(--themeColor)" class="float-right" @click="() => { showWebhookBuilder = false; originalStateFromDb = null;}">
                <v-icon>$fas_times</v-icon>
            </v-btn>
          </div>

          <layout-with-tabs title="" :tabs="['Info', 'Result']" ref="layoutWithTabs">
              <template slot="Info">
                <div style="margin-top: 12px">
                  <webhook-builder v-if="showWebhookBuilder" :originalStateFromDb="originalStateFromDb" @saveWebhook="saveWebhook" @saveWebhookAndRun="saveWebhookAndRun" :loading="loading"/>
                </div>
              </template>
              <template slot="Result">
                <div style="margin-top: 12px;">
                  <sample-data v-if="this.customWebhookResult && this.customWebhookResult['message']" requestTitle="Webhook Request" responseTitle="Webhook Response" :json="json"></sample-data>
                  <div v-else>No results saved</div>
                </div>
              </template>
          </layout-with-tabs>

        </div>
    </v-dialog>

  </div>
</template>

<script>

import SimpleTable from "../../../../../shared/components/SimpleTable";
import WebhookBuilder from "./WebhookBuilder";
import api from "../api";
import func from "../../../../../../../util/func";
import LayoutWithTabs from '@/apps/dashboard/layouts/LayoutWithTabs'
import SampleData from "../../../../../shared/components/SampleData";

export default {
    name: "WebhookIntegration",
    components: {
      SimpleTable,
      WebhookBuilder,
      LayoutWithTabs,
      SampleData
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
              value: 'createTimePrettify'
          },
          {
              text: 'status',
              value: 'activeStatus'
          },
          {
            text: 'Last sent',
            value: 'lastSentTimestampPrettify'
          },
        ],
        webhooks:[],
        customWebhookResult: null,
        actions: [ 
          {
              isValid: item => true,
              icon: item => item.activeStatus === "ACTIVE" ? '$fas_stop' :'$fas_play' ,
              text: item => item.activeStatus === "ACTIVE" ? 'Deactivate' : 'Activate',
              func: item => this.changeStatus(item),
              success: (resp, item) => func.showSuccessSnackBar("Success!"),
              failure: (err, item) => func.showErrorSnackBar("Failed!"),
          },
        ],
      }
    },
    methods: {
      createNewWebhookFn() {
        this.originalStateFromDb = null;
        this.showWebhookBuilder = true
        this.customWebhookResult = {}
      },
      openWebhookBuilder(item) {
        this.showWebhookBuilder = true
        this.originalStateFromDb = item
        this.customWebhookResult = {}
        api.fetchLatestWebhookResult(item.id).then((resp) => {
          if (!resp.customWebhookResult) {
            this.customWebhookResult = {}
          } else {
            this.customWebhookResult = {...resp.customWebhookResult}
          }
        })
      },
      openWebhookResult() {
        this.showWebhookResult = true
      },
      prettifyWebhooks(x) {
            x["createTimePrettify"] = func.prettifyEpoch(x["createTime"])
            let lastSentTimestamp = x["lastSentTimestamp"]
            x["lastSentTimestampPrettify"] = lastSentTimestamp === 0 ? "-" :  func.prettifyEpoch(lastSentTimestamp);
        return x
      },
      changeStatus(item) {
        let newStatus = item.activeStatus === "ACTIVE" ? "INACTIVE" : "ACTIVE"
        let result = api.changeStatus(item.id, newStatus)

        result.then((resp) => {
          this.webhooks.forEach((x) => {
            if (x.id === item.id) {
              x["activeStatus"] = newStatus
            }
          })


        })


        return result
      },
      fetchWebhooks() {
        api.fetchCustomWebhooks().then((resp) => {
          let customWebhooks = resp.customWebhooks;
          this.webhooks = customWebhooks.map(this.prettifyWebhooks)
        })
      },
      saveWebhookMain(data) {
          let updatedData = data["updatedData"]

          let webhookName = updatedData["webhookName"]
          if (!webhookName) {
              func.showErrorSnackBar("Invalid name")
              return
          }

          let url = updatedData["url"]
          if (!url) {
              func.showErrorSnackBar("Invalid URL")
              return
          }

          let queryParams = updatedData["queryParams"]

          let method = updatedData["method"]
          method = this.validateMethod(method)
          if (!method) {
              func.showErrorSnackBar("Invalid HTTP method")
              return
          }

          let headerString =  updatedData["headerString"]
          let frequencyInSeconds = updatedData["frequencyInSeconds"]

          let body = updatedData["body"]
          let selectedWebhookOptions = updatedData["selectedWebhookOptions"]
          let newEndpointCollections = updatedData["newEndpointCollections"]
          let newSensitiveEndpointCollections = updatedData["newSensitiveEndpointCollections"]

          let createNew = data["createNew"]
          if (createNew) {
            return api.addCustomWebhook(webhookName, url, queryParams, method, headerString, body, frequencyInSeconds, selectedWebhookOptions, newEndpointCollections, newSensitiveEndpointCollections)
          } else {
            let id = this.originalStateFromDb["id"]
            return api.updateCustomWebhook(id, webhookName, url, queryParams, method, headerString, body, frequencyInSeconds, selectedWebhookOptions, newEndpointCollections, newSensitiveEndpointCollections)
          }
      },
      saveWebhook(data) {
          this.loading= true
          let createNew = data["createNew"]
          let result = this.saveWebhookMain(data)
          if (!result) {
              this.loading = false
              return
          }

          result.then((resp) => {
              let customWebhooks = resp.customWebhooks;
              this.webhooks = [].concat(customWebhooks.map(this.prettifyWebhooks))
              this.loading = false
              func.showSuccessSnackBar("Webhook saved successfully!")
          }).catch((err) => {
              console.log(err);
              this.loading = false
          })

          if(createNew){
            this.showWebhookBuilder = false
          }
      },
      saveWebhookAndRun(data){
        this.loading= true
          let createNew = data["createNew"]
          let result = this.saveWebhookMain(data)
          if (!result) {
              this.loading = false
              return
          }

          result.then((resp) => {
              let customWebhooks = resp.customWebhooks;
              this.webhooks = [].concat(customWebhooks.map(this.prettifyWebhooks))
              this.loading = false

              let id = this.originalStateFromDb["id"]
              let runResult = api.runOnce(id);
              runResult.then((resp) => {
                func.showSuccessSnackBar("Webhook saved and ran successfully!")
              }).catch((err) => {
                console.log(err);
                this.loading = false
              })
          }).catch((err) => {
              console.log(err);
              this.loading = false
          })

          if(createNew){
            this.showWebhookBuilder = false
          }
      },
      validateMethod(methodName) {
          let m = methodName.toUpperCase()
          let allowedMethods = ["GET", "POST", "PUT", "DELETE", "HEAD", "OPTIONS", "TRACE", "PATCH"]
          let idx = allowedMethods.indexOf(m);
          if (idx === -1) return null
          return allowedMethods[idx]
      }
    },
    mounted() {
      this.fetchWebhooks()
    },
    computed : {
      json: function() {
        if (!this.customWebhookResult) return null
        let message = this.customWebhookResult["message"]
        if (!message) message = "{}"
        return {
            "message": JSON.parse(message),
            title: "Sample data",
            "highlightPaths": []
        }
      }
    }
}
</script>

<style scoped lang="sass">


</style>
