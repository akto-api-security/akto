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
      <!-- <div
          v-for="value, header, index in headers" :key="index"
            class="sample-data-headers" 
        >
            <span class="sample-data-headers-key">{{header}}</span>
            <span class="sample-data-headers-val">: {{value}}</span>
        </div> -->
        <!-- <div class="sample-data-message">{{data}}</div> -->
          <div class="wrapper">
            <json-viewer
              :contentJSON="data['json']"
              :errors="{}" 
              :highlightItemMap="data['highlightPaths']" 
              />
          </div>
    </div>
</template>

<script>

import obj from "@/util/obj"
import api from "@/apps/dashboard/views/observe/inventory/api";
import JsonViewer from "@/apps/dashboard/shared/components/JSONViewer"

export default {
    name: "SampleSingleSide",
    components: {
        JsonViewer
    },
    props: {
        title: obj.strR,
        firstLine: obj.strR,
        headers: obj.objR,
        data: obj.objR,
        completeData: obj.objR,
        simpleCopy: obj.boolR
    },
    methods: {
      copyToClipboard(text) {
          if (window.clipboardData && window.clipboardData.setData) {
              // Internet Explorer-specific code path to prevent textarea being shown while dialog is visible.
              return window.clipboardData.setData("Text", text);

          }
          else if (document.queryCommandSupported && document.queryCommandSupported("copy")) {
              var textarea = document.createElement("textarea");
              textarea.textContent = text;
              textarea.style.position = "fixed";  // Prevent scrolling to bottom of page in Microsoft Edge.
              document.body.appendChild(textarea);
              textarea.select();
              try {
                  return document.execCommand("copy");  // Security exception may be thrown by some browsers.
              }
              catch (ex) {
                  // console.warn("Copy to clipboard failed.", ex);
                  // return prompt("Copy to clipboard: Ctrl+C, Enter", text);
              }
              finally {
                  document.body.removeChild(textarea);
              }
          }
      },

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
          this.copyToClipboard(d)
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
           
        },
        dd : function() {
          // let b = JSON.parse(this.data)
          return b
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


<style lang="scss">
.wrapper .value-key {
  color: #47466A99;
  padding: 1px 5px 2px 10px;
}

.wrapper .data-key {
  color: #47466A;
  padding: 1px 5px 2px 7px;
}

.wrapper .chevron-arrow {
  border-right: 2px solid lightgrey;
  border-bottom: 2px solid lightgrey;
}
.wrapper .json-view-item:not(.root-item) {
  border-left: 1px dashed lightgrey;
}
</style>