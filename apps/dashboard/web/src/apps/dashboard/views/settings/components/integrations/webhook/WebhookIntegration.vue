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
        :actions="actions"
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
import func from "../../../../../../../util/func";

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
              value: 'createTimePrettify'
          },
          {
              text: 'Status',
              value: 'activeStatus'
          }
        ],
        webhooks:[],

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
      openWebhookBuilder(item) {
        this.showWebhookBuilder = true
        this.originalStateFromDb = item
      },
      openWebhookResult() {
        this.showWebhookResult = true
      },
      prettifyWebhooks(x) {
            x["createTimePrettify"] = func.prettifyEpoch(x["createTime"])
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

          let createNew = data["createNew"]
          if (createNew) {
            return api.addCustomWebhook(webhookName, url, queryParams, method, headerString, body, frequencyInSeconds)
          } else {
            let id = this.originalStateFromDb["id"]
            return api.updateCustomWebhook(id, webhookName, url, queryParams, method, headerString, body, frequencyInSeconds)
          }
      },
      saveWebhook(data) {
          this.loading= true
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