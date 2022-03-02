<template>
    <div class="sample-data-container">
      <div class="d-flex" style="justify-content: space-between">
        <div class="sample-data-title">{{title}}</div>
        <v-btn plain @click="copyRequest">
          <v-tooltip bottom>
            <template v-slot:activator='{ on, attrs }'>
              <v-icon
                  size=16
                  class="tray-button"
                  v-bind="attrs"
                  v-on="on"
              >
                $fas_copy
              </v-icon>
            </template>
            <span>{{tooltipValue}}</span>
          </v-tooltip>
        </v-btn>
      </div>
      <div class="sample-data-line">{{firstLine}}</div>
      <div
          v-for="value, header, index in headers" :key="index"
            class="sample-data-headers" 
        >
            <span class="sample-data-headers-key">{{header}}</span>
            <span class="sample-data-headers-val">: {{value}}</span>
        </div>
        <div class="sample-data-message">{{data}}</div>
    </div>
</template>

<script>

import obj from "@/util/obj"
import api from "@/apps/dashboard/views/observe/inventory/api";

export default {
    name: "SampleSingleSide",
    props: {
        title: obj.strR,
        firstLine: obj.strR,
        headers: obj.objR,
        data: obj.strR,
        completeData: obj.objR,
        simpleCopy: obj.boolR
    },
    methods: {
      async copyRequest() {
        let d = "";
        let snackBarMessage = ""
        if (this.simpleCopy) {
          let b = {}
          b["responsePayload"] = this.data ? JSON.parse(this.data): {}
          b["responseHeaders"] = this.headers
          b["statusCode"] = this.completeData.statusCode
          d = JSON.stringify(b)
          snackBarMessage = "Response data copied to clipboard"
        } else {
          let resp = await api.convertSampleDataToCurl(JSON.stringify(this.completeData))
          d = resp.curlString
          snackBarMessage = "Curl request copied to clipboard"
          console.log("Here is your curl request")
          console.log(d);
        }

        if (d) {
          navigator.clipboard.writeText(d)
          window._AKTO.$emit('SHOW_SNACKBAR', {
            show: true,
            text: snackBarMessage,
            color: 'green'
          });
        }
      }
    },

    computed: {
        tooltipValue: function() {
          return this.simpleCopy ? "Copy response values": "Copy as curl"
           
        }
    }
}
</script>

<style scoped lang="sass">
.sample-data
    &-container
        background-color: #47466A0D
        font-size: 13px
        padding: 8px
        color: #47466A
        margin: 8px 8px 0 0 
    &-title
        font-weight: 500
        text-transform: uppercase
        padding-bottom: 16px    
    &-line
        padding-bottom: 16px    
    &-headers
        overflow-wrap: anywhere
        &-key
            font-weight: 500  
        &-val
            color: #47466A99
      
    &-message
        padding-top: 16px   
        color: #47466A99 
        overflow-wrap: anywhere

</style>