<template>
    <div class="sample-data-container">
      <div class="d-flex" style="justify-content: space-between">
        <div class="sample-data-title">{{title}}</div>
        <div class="copy-icon">
          <simple-menu v-if="!simpleCopy" :items="copyMenuItems" attach="passedId">
            <template v-slot:activator2>
              <v-icon
                  size=16
                  class="tray-button"
                  id="passedId"
              >
                $fas_copy
              </v-icon>
            </template>
          </simple-menu>
          <v-tooltip bottom v-else attach="#copyIcon" min-width="100">
            <template v-slot:activator='{ on, attrs }'>
              <v-icon
                  size=16
                  class="tray-button"
                  v-bind="attrs"
                  v-on="on"
                  @click="copyRequest"
                  id="copyIcon"
              >
                $fas_copy
              </v-icon>
            </template>
            <span>{{tooltipValue}}</span>
          </v-tooltip>
        </div>
      </div>

      <div class="sample-data-line">
        <v-tooltip bottom :disabled="!this.firstLineToolTipValue">
          <template v-slot:activator="{on, attrs}">
            <div v-on="on" v-bind="attrs">
              {{firstLine}}
            </div>
          </template>
          <span>
            {{this.firstLineToolTipValue}}
          </span>
        </v-tooltip>
      </div>

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
import SimpleMenu from "@/apps/dashboard/shared/components/SimpleMenu"

export default {
    name: "SampleSingleSide",
    components: {
        JsonViewer,
        SimpleMenu
    },
    props: {
        title: obj.strR,
        firstLine: obj.strR,
        firstLineToolTipValue: obj.strN,
        headers: obj.objR,
        data: obj.objR,
        completeData: obj.objR,
        simpleCopy: obj.boolR
    },
    data() {
      return {
        copyMenuItems: [
          {
            label: "Copy as curl",
            click: () => this.copyRequest("CURL")
          },
          {
            label: "Copy as burp request",
            click: () => this.copyRequest("BURP")
          }
        ]
      }
    },
    methods: {
      copyToClipboard(text) {
          // main reason to use domElement like this instead of document.body is that execCommand works only if current
          // component is not above normal document. For example in testing page, we show SampleSingleSide.vue in a v-dialog
          // NOTE: Do not use navigator.clipboard because it only works for HTTPS sites
          let domElement = this.$el;
          if (window.clipboardData && window.clipboardData.setData) {
              // Internet Explorer-specific code path to prevent textarea being shown while dialog is visible.
              return window.clipboardData.setData("Text", text);

          }
          else if (document.queryCommandSupported && document.queryCommandSupported("copy")) {
              var textarea = document.createElement("textarea");
              textarea.textContent = text;
              textarea.style.position = "fixed";  // Prevent scrolling to bottom of page in Microsoft Edge.
              domElement.appendChild(textarea);
              textarea.focus();
              textarea.select();
              textarea.setSelectionRange(0, 99999);
              try {
                  return document.execCommand("copy");  // Security exception may be thrown by some browsers.
              }
              catch (ex) {
                  // console.warn("Copy to clipboard failed.", ex);
                  // return prompt("Copy to clipboard: Ctrl+C, Enter", text);
              }
              finally {
                  domElement.removeChild(textarea);
              }
          }
      },

      async copyRequest(type) {
        let copyString = "";
        let snackBarMessage = ""
        if (this.simpleCopy) {
          let responsePayload = {}
          let responseHeaders = {}
          let statusCode = 0

          if (this.completeData) {
            responsePayload = this.completeData["response"] ?  this.completeData["response"]["body"] : this.completeData["responsePayload"]
            responseHeaders = this.completeData["response"] ?  this.completeData["response"]["headers"] : this.completeData["responseHeaders"]
            statusCode = this.completeData["response"] ?  this.completeData["response"]["statusCode"] : this.completeData["statusCode"]
          }

          let b = {
            "responsePayload": responsePayload,
            "responseHeaders": responseHeaders,
            "statusCode": statusCode
          }

          copyString = JSON.stringify(b)
          snackBarMessage = "Response data copied to clipboard"
        } else {
          if (type === "CURL") { 
            snackBarMessage = "Curl request copied to clipboard"
            let resp = await api.convertSampleDataToCurl(JSON.stringify(this.completeData))
            copyString = resp.curlString
          } else {
            snackBarMessage = "Burp request copied to clipboard"
            let resp = await api.convertSampleDataToBurpRequest(JSON.stringify(this.completeData))
            copyString = resp.burpRequest
          }

          console.log("Here is your curl request")
          console.log(copyString);
        }

        if (copyString) {
          this.copyToClipboard(copyString)
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
          return this.simpleCopy ? "Copy response": "Copy as curl"
           
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
        background-color: var(--themeColorDark17)
        font-size: 13px
        padding: 8px
        color: var(--themeColorDark)
        height: 100%
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
            color: var(--themeColorDark7)
      
    &-message
        padding-top: 16px   
        color: var(--themeColorDark7) 
        overflow-wrap: anywhere

</style>

<style scoped>
  .copy-icon >>> .v-menu__content {
    top: 30px !important;
    left: 0 !important;
  }
  .copy-icon >>> .v-tooltip__content{
    top: 30px !important;
    left: unset !important;
    margin-left: -8px !important;
    padding-left: 8px !important;
  }
</style>


<style lang="scss">
.wrapper .value-key {
  color: var(--themeColorDark7);
  padding: 1px 5px 2px 10px;
}

.wrapper .data-key {
  color: var(--themeColorDark);
  padding: 1px 5px 2px 7px;
}

.wrapper .chevron-arrow {
  border-right: 2px solid lightgrey;
  border-bottom: 2px solid lightgrey;
}
.wrapper .json-view-item:not(.root-item) {
  border-left: 1px dashed lightgrey;
}

.copy-icon {
  padding-right: 16px;
  padding-top: 2px;
}
</style>