<template>
  <div>
    <div style="display: flex; padding-bottom: 24px;">
      <div style="width: 100%">
        <div class="request-title">Webhook name</div>
        <template-string-editor 
          :defaultText="this.updatedData['webhookName']"
          :onChange=onChangeName
        />

        <div class="request-title">URL</div>
        <template-string-editor 
          :defaultText="this.updatedData['url']"
          :onChange=onChangeURL
        />

        <div class="request-title">Query params</div>
        <template-string-editor 
          :defaultText="this.updatedData['queryParams']"
          :onChange=onChangeQueryParams
        />

        <div class="request-title">Method</div>
        <template-string-editor 
          :defaultText="this.updatedData['method']"
          :onChange=onChangeMethod
        />

        <div class="request-title">Headers</div>
        <template-string-editor 
          :defaultText="this.updatedData['headerString']"
          :onChange=onChangeHeaders
        />

        <div class="request-title">Body</div>
        <template-string-editor 
          :defaultText="this.updatedData['body']"
          :onChange=onChangeBody
        />

      </div>  
    </div>

    <div style="height: 24px"></div>

    <div class="d-flex">
        <div class="request-title">Run every</div>
        <div class="text-center" v-for="(item, index) in this.intervals" :key="item.value">
            <v-chip class="ma-2" 
                :color="isIntervalSelected(item) ? 'green' : ''" 
                :text-color="isIntervalSelected(item) ? 'white' : 'black'"
                @click="intervalSelected(item)"
            >
                {{item.name}}
            </v-chip>
        </div>
    </div>

    <div class="d-flex jc-end ma-2">
        <v-btn class = "mx-2" primary dark color="var(--themeColor)" @click="saveWebhook" :disabled="!updatedData" :loading="loading">
            Save
        </v-btn>
        <v-btn primary dark color="var(--themeColor)" @click="saveWebhookAndRun" :disabled="!updatedData" :loading="loading">
            Save and run once
        </v-btn>
    </div>
  </div>
</template>

<script>

import TemplateStringEditor from "../../../../testing/components/react/TemplateStringEditor.jsx";
import obj from "../../../../../../../util/obj";

export default {
    name: "WebhookIntegration",
    components: {
      'template-string-editor' : TemplateStringEditor
    },
    props: {
        originalStateFromDb: obj.objN,
        loading: obj.boolN
    },
    data () {
      return {
        defaultWebhookName: "",
        defaultUrl: "",
        defaultQueryParams: "",
        defaultMethod: "POST",
        defaultHeaderString: "{'content-type': 'application/json'}",
        defaultBody: "{}",
        intervals: [
            {"name": "15 mins", "value": 900},
            {"name": "30 mins", "value": 1800},
            {"name": "1 hour", "value":  3600},
            {"name": "6 hours", "value": 21600},
            {"name": "12 hours", "value": 43200},
            {"name": "24 hours", "value": 86400}
        ]
      }
    },
    methods: {
        saveWebhook() {
            this.$emit("saveWebhook", {"updatedData": this.updatedData, "createNew": this.originalStateFromDb == null})
        },
        saveWebhookAndRun(){
            this.$emit("saveWebhookAndRun", {"updatedData": this.updatedData, "createNew": this.originalStateFromDb == null})
        },
        onChangeName(newData) {
            this.onChange("webhookName", newData)
        },
        onChangeURL(newData) {
            this.onChange("url", newData)
        },
        onChangeMethod(newData) {
            this.onChange("method", newData)
        },
        onChangeQueryParams(newData) { 
            this.onChange("queryParams", newData)
        },
        onChangeHeaders(newData) {
            this.onChange("headerString", newData)
        },
        onChangeBody(newData) {
            this.onChange("body", newData)
        },
        onChange(key, newData) {
            this.updatedData[key] = newData
        },
        isIntervalSelected(item){
            return this.updatedData['frequencyInSeconds'] === item.value
        },
        intervalSelected(item) {
            this.onChange("frequencyInSeconds", item.value)
            this.intervals = [].concat(this.intervals)
        }

    },
    computed: {
        color(item){

        },
        updatedData() {
            if (this.originalStateFromDb) return {...this.originalStateFromDb}
            return {
                "webhookName": this.defaultWebhookName,
                "url": this.defaultUrl,
                "queryParams": this.defaultQueryParams,
                "method": this.defaultMethod,
                "headerString": this.defaultHeaderString,
                "body": this.defaultBody,
                "frequencyInSeconds": 86400
            }
        }
    },
    async mounted() {

    }
}
</script>

<style lang="sass">

.request-title
  padding-top: 12px
  font-size: 14px
  color: var(--themeColorDark)
  font-family: 'Poppins'
  font-weight: 500

.request-editor 
  font-size: 14px !important


</style>