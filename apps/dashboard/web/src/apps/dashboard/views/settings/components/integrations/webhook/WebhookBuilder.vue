<template>
  <div>
    <div style="display: flex; padding-bottom: 24px;">
      <div style="width: 100%">
        <div class="request-title">Webhook name</div>
        <template-string-editor :defaultText="this.updatedData['webhookName']" :onChange=onChangeName />

        <div class="request-title">URL</div>
        <template-string-editor :defaultText="this.updatedData['url']" :onChange=onChangeURL />

        <div class="request-title">Query params</div>
        <template-string-editor :defaultText="this.updatedData['queryParams']" :onChange=onChangeQueryParams />

        <!-- <div class="request-title">Method</div>
        <template-string-editor 
          :defaultText="this.updatedData['method']"
          :onChange=onChangeMethod
        /> -->

        <div class="request-title">Headers</div>
        <template-string-editor :defaultText="this.updatedData['headerString']" :onChange=onChangeHeaders />

        <div class="request-title">Body</div>
        <template-string-editor :defaultText="this.updatedData['body']" :onChange=onChangeBody />
        <div class="request-title">Options</div>

        <!-- @clickedItem="appliedFilter(filterMenu.value, $event)"
                    @selectedAll="selectedAll(filterMenu.value, $event)" 

          @operatorChanged="operatorChanged(filterMenu.value, $event)"  -->

        <filter-list title="Options" :items="customWebhookOptions"
        @clickedItem="selectedOption($event)"
          hideOperators hideListTitle
          />

      </div>
    </div>

    <div style="height: 24px"></div>

    <div class="d-flex">
      <div class="request-title">Run every</div>
      <div class="text-center" v-for="(item, index) in this.intervals" :key="item.value">
        <v-chip class="ma-2" :color="isIntervalSelected(item) ? 'green' : ''"
          :text-color="isIntervalSelected(item) ? 'white' : 'black'" @click="intervalSelected(item)">
          {{ item.name }}
        </v-chip>
      </div>
    </div>

    <div class="d-flex jc-end ma-2">
      <v-btn class="mx-2" primary dark color="var(--themeColor)" @click="saveWebhook" :disabled="!updatedData"
        :loading="loading">
        Save
      </v-btn>
      <v-btn primary dark color="var(--themeColor)" @click="saveWebhookAndRun" :disabled="!updatedData"
        :loading="loading">
        Save and run once
      </v-btn>
    </div>
  </div>
</template>

<script>

import TemplateStringEditor from "../../../../testing/components/react/TemplateStringEditor.jsx";
import obj from "../../../../../../../util/obj";
import FilterList from '@/apps/dashboard/shared/components/FilterList'

export default {
  name: "WebhookIntegration",
  components: {
    'template-string-editor': TemplateStringEditor,
    FilterList
  },
  props: {
    originalStateFromDb: obj.objN,
    loading: obj.boolN
  },
  data() {
    return {
      defaultWebhookName: "",
      defaultUrl: "",
      defaultQueryParams: "",
      defaultMethod: "POST",
      defaultHeaderString: "{'content-type': 'application/json'}",
      defaultBody: "{}",
      intervals: [
        { "name": "15 mins", "value": 900 },
        { "name": "30 mins", "value": 1800 },
        { "name": "1 hour", "value": 3600 },
        { "name": "6 hours", "value": 21600 },
        { "name": "12 hours", "value": 43200 },
        { "name": "24 hours", "value": 86400 }
      ],
      customWebhookOptions: [
        { "title": "New endpoint", "checked": false, "value": "${AKTO.changes_info.newEndpoints}" },
        { "title": "New endpoint count", "checked": false, "value": "${AKTO.changes_info.newEndpointsCount}" },
        { "title": "New sensitive endpoint", "checked": false, "value": "${AKTO.changes_info.newSensitiveEndpoints}" },
        { "title": "New sensitive endpoint count", "checked": false, "value": "${AKTO.changes_info.newSensitiveEndpointsCount}" },
        { "title": "New sensitive parameter count", "checked": false, "value": "${AKTO.changes_info.newSensitiveParametersCount}" },
        { "title": "New parameter count", "checked": false, "value": "${AKTO.changes_info.newParametersCount}" }
      ]
    }
  },
  methods: {
    
    selectedOption($event) {
      let body = {}
      let item = $event.item
      let checked = $event.checked
      this.customWebhookOptions.forEach((i) => {
          if (i.value === item.value) {
            i.checked = checked
          }
          if (i.checked) {
            body[i.title] = i.value

          }
        })
      this.onChangeBody(JSON.stringify(body))

    },
    saveWebhook() {
      this.$emit("saveWebhook", { "updatedData": this.updatedData, "createNew": this.originalStateFromDb == null })
    },
    saveWebhookAndRun() {
      this.$emit("saveWebhookAndRun", { "updatedData": this.updatedData, "createNew": this.originalStateFromDb == null })
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
    isIntervalSelected(item) {
      return this.updatedData['frequencyInSeconds'] === item.value
    },
    intervalSelected(item) {
      this.onChange("frequencyInSeconds", item.value)
      this.intervals = [].concat(this.intervals)
    }

  },
  computed: {
    updatedData() {
      if (this.originalStateFromDb) return { ...this.originalStateFromDb }
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
  mounted() {
    if (this.originalStateFromDb) {
      this.customWebhookOptions.forEach((item) => {
        debugger
        if (this.originalStateFromDb['body'].includes(item.value)) {
          item.checked = true
        }
      })
    }
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