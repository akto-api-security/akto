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

        <div class="request-title">Headers</div>
        <template-string-editor :defaultText="this.updatedData['headerString']" :onChange=onChangeHeaders />

        <div class="request-title">Options</div>

        <v-list>
          <v-list-item v-for="(item, index) in customWebhookOptions" :key="index">
            <v-btn icon primary plain :ripple="false" @click="selectedOption(item)" class="checkbox-btn">
              <v-icon>
                {{ checkedMap[item.value] ? '$far_check-square' : '$far_square' }}
              </v-icon>
            </v-btn>
            <v-list-item-content>
              <div class="display-flex">
                <span class="item-label">{{ item.title }}</span>
                <v-menu v-if="item.title === 'New endpoint' && checkedMap[item.value]" offset-y :close-on-content-click="false">
                  <template v-slot:activator="{ on, attrs }">
                    <v-btn class="ml-3" v-bind="attrs" v-on="on" primary>
                      <span>collections</span>
                      <v-icon>$fas_angle-down</v-icon>
                    </v-btn>
                  </template>
                  <filter-list title="Collections" :items="getAllCollectionsForFilterList('newEndpointCollections')"
                    @clickedItem="clickedNewEndpointCollection($event)"
                    @selectedAll="globalCheckboxNewEndpointCollection($event)" hideOperators></filter-list>
                </v-menu>
                <v-menu v-if="item.title === 'New sensitive endpoint' && checkedMap[item.value]" offset-y :close-on-content-click="false">
                  <template v-slot:activator="{ on, attrs }">
                    <v-btn class="ml-3" v-bind="attrs" v-on="on" primary>
                      <span>collections</span>
                      <v-icon>$fas_angle-down</v-icon>
                    </v-btn>
                  </template>
                  <filter-list title="Collections" :items="getAllCollectionsForFilterList('newSensitiveEndpointCollections')"
                    @clickedItem="clickedNewSensitiveEndpointCollection($event)"
                    @selectedAll="globalCheckboxNewSensitiveEndpointCollection($event)" hideOperators></filter-list>
                </v-menu>
              </div>
            </v-list-item-content>
          </v-list-item>
        </v-list>
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
  name: "WebhookBuilder",
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
      checkedMap: [],
      defaultWebhookName: "",
      defaultUrl: "",
      defaultQueryParams: "",
      defaultMethod: "POST",
      defaultHeaderString: "{'content-type': 'application/json'}",
      defaultBody: "{}",
      defaultSelectedWebhookOptions: [],
      defaultSelectednewEndpointCollections: [],
      defaultSelectednewSensitiveEndpointCollections: [],
      intervals: [
        { "name": "15 mins", "value": 900 },
        { "name": "30 mins", "value": 1800 },
        { "name": "1 hour", "value": 3600 },
        { "name": "6 hours", "value": 21600 },
        { "name": "12 hours", "value": 43200 },
        { "name": "24 hours", "value": 86400 }
      ],
      customWebhookOptions: [
        { "title": "New endpoint", "checked": false, "value": "NEW_ENDPOINT" },
        { "title": "New endpoint count", "checked": false, "value": "NEW_ENDPOINT_COUNT" },
        { "title": "New sensitive endpoint", "checked": false, "value": "NEW_SENSITIVE_ENDPOINT" },
        { "title": "New sensitive endpoint count", "checked": false, "value": "NEW_SENSITIVE_ENDPOINT_COUNT" },
        { "title": "New sensitive parameter count", "checked": false, "value": "NEW_SENSITIVE_PARAMETER_COUNT" },
        { "title": "New parameter count", "checked": false, "value": "NEW_PARAMETER_COUNT" }
      ]
    }
  },
  methods: {
    clickedNewEndpointCollection($event) {
      let newEndpointCollections = this.updatedData['newEndpointCollections']
      if (newEndpointCollections == null) {
        newEndpointCollections = []
      }
      let checked = $event.checked
      if (checked) {//include in collection
        if (!newEndpointCollections.includes($event.item.title)) {
          newEndpointCollections.push($event.item.title)
        }
      } else {//remove from collection
        newEndpointCollections = newEndpointCollections.filter((item) => {
          return item !== $event.item.title
        })
      }
      this.onChange("newEndpointCollections", newEndpointCollections)
    },
    clickedNewSensitiveEndpointCollection($event) {
      let newSensitiveEndpointCollections = this.updatedData['newSensitiveEndpointCollections']
      if (newSensitiveEndpointCollections == null) {
        newSensitiveEndpointCollections = []
      }
      let checked = $event.checked
      if (checked) {//include in collection
        if (!newSensitiveEndpointCollections.includes($event.item.title)) {
          newSensitiveEndpointCollections.push($event.item.title)
        }
      } else {//remove from collection
        newSensitiveEndpointCollections = newSensitiveEndpointCollections.filter((item) => {
          return item !== $event.item.title
        })
      }
      this.onChange("newSensitiveEndpointCollections", newSensitiveEndpointCollections)
    },
    globalCheckboxNewEndpointCollection($event) {
      let checked = $event.checked
      let items = $event.items
      let newEndpointCollections = this.updatedData['newEndpointCollections']
      if (newEndpointCollections == null) {
        newEndpointCollections = []
      }
      if (checked) {//global checkbox selected
        items.forEach((item) => {
          if (!newEndpointCollections.includes(item.title)) {
            newEndpointCollections.push(item.title)
          }
        })
      } else {//global checkbox unselected
        newEndpointCollections = newEndpointCollections.filter((item) => {
          return !items.includes(item)
        })
      }
      this.onChange("newEndpointCollections", newEndpointCollections)
    },
    globalCheckboxNewSensitiveEndpointCollection($event) {
      let checked = $event.checked
      let items = $event.items
      let newSensitiveEndpointCollections = this.updatedData['newSensitiveEndpointCollections']
      if (newSensitiveEndpointCollections == null) {
        newSensitiveEndpointCollections = []
      }
      if (checked) {//global checkbox selected
        items.forEach((item) => {
          if (!newSensitiveEndpointCollections.includes(item.title)) {
            newSensitiveEndpointCollections.push(item.title)
          }
        })
      } else {//global checkbox unselected
        newSensitiveEndpointCollections = newSensitiveEndpointCollections.filter((item) => {
          return !items.includes(item)
        })
      }
      this.onChange("newSensitiveEndpointCollections", newSensitiveEndpointCollections)
    },
    selectedOption(item) {
      this.checkedMap[item.value] = !this.checkedMap[item.value]//fliping the value
      let checked = this.checkedMap[item.value]
      let selectedWebhookOptions = []
      this.customWebhookOptions.forEach((i) => {
        if (i.value === item.value) {
          i.checked = checked
        }
        if (i.checked) {
          selectedWebhookOptions.push(i.value)
        }
      })
      this.onChange("selectedWebhookOptions", selectedWebhookOptions)
    },
    getAllCollectionsForFilterList(optionName) {
      let item = []
      let optionSelected = this.updatedData[optionName]
      if (optionSelected == null) {
        optionSelected = []
      }
      Object.keys(this.mapCollectionIdToName).forEach(element => {
        if (optionSelected.includes(this.mapCollectionIdToName[element])) {
          item.push({ title: this.mapCollectionIdToName[element],checked: true, value: element })
        } else {
          item.push({ title: this.mapCollectionIdToName[element],checked:false, value: element })
        }
      })
      return item
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
        "selectedWebhookOptions": this.defaultSelectedWebhookOptions,
        "selectedNewEndpointCollections": this.defaultSelectednewEndpointCollections,
        "selectedNewSensitiveEndpointCollections": this.defaultSelectednewSensitiveEndpointCollections,
        "frequencyInSeconds": 86400
      }
    },
    mapCollectionIdToName() {
      return this.$store.state.collections.apiCollections.reduce((m, e) => {
        m[e.id] = e.displayName
        return m
      }, {})
    }
  },
  mounted() {
    if (this.originalStateFromDb) {
      this.customWebhookOptions.forEach((item) => {
        if (this.originalStateFromDb['selectedWebhookOptions'].includes(item.value)) {
          item.checked = true
        }
      })
    }
    this.checkedMap = this.customWebhookOptions.reduce((m, i) => {
      if (i.checked) {
        m[i.value] = true
      } else {
        m[i.value] = false
      }
      return m
    }, {})
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