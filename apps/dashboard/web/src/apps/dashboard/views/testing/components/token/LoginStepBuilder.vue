<template>
  <div class="pt-2">
    <div class="login-builder-container">
    
      <div>
        <div style="display: flex; padding-bottom: 24px;">
          <div style="width: 100%">

          <icon-menu v-if="!this.tabData.data" icon="$fas_caret-down" :items="dropDownItems"/>

          <div :style='this.showOtpForm ? "" : "display: none"'>

              <div class="input-value">
                          <v-text-field 
                              value="Connect with zapier to send data to webhook url"
                              style="width: 700px"
                              readonly
                          />
                      </div>

              <div class="input-value">
                          <v-text-field 
                              :value="this.webhookUrl"
                              style="width: 700px"
                              readonly
                          />
                      </div>
                
              <v-btn dark outlined primary color="#6200EA" @click="pollOtpResponse">
                  Zapier Setup Done
              </v-btn>

               <div class="request-title">Regex</div>
                  <template-string-editor
                    :defaultText="this.updatedData['regex']"
                    :onChange=onChangeRegex
                  />
                 
              <v-btn :disabled="!finishedWebhookSetup" dark outlined primary color="#6200EA" @click="testRegex">
                  Test Regex
              </v-btn>

              <v-btn :disabled="disableOtpSave" dark outlined primary color="#6200EA" @click="testSingleStep">
                Save
             </v-btn>
            
          </div>

          <div  :style='this.showOtpForm ? "display: none" : ""'>

            <div class="request-title">URL</div>
            <template-string-editor
              :defaultText="this.updatedData['url']"
              :onChange=onChangeURL
            />

            <div class="request-title">Query params</div>
            <template-string-editor
              :defaultText="this.updatedData['queryParams'] || ' '"
              :onChange=onChangeQueryParams
            />

            <div class="request-title">Method</div>
            <template-string-editor
              :defaultText="this.updatedData['method']"
              :onChange=onChangeMethod
            />

            <div class="request-title">Headers</div>
            <template-string-editor
              :defaultText="this.updatedData['headers']"
              :onChange=onChangeHeaders
            />

            <div class="request-title">Body</div>
            <template-string-editor
              :defaultText="this.updatedData['body']"
              :onChange=onChangeBody
            />

            <v-btn dark outlined primary color="#6200EA" @click="testSingleStep">
                Test
            </v-btn>

          </div>

          </div>  
        </div>

      </div>

      <div class="response-data pr-2">
        <div v-if="hasResponseData" style="height: 400px">
          <div class="request-title">[Response] Headers</div>
          <div class="request-editor request-editor-headers">
            {{tabData.responseHeaders}}
          </div>
          <div class="request-title">[Response] Payload</div>
          <div class="request-editor request-editor-payload">
            {{tabData.responsePayload}}
          </div> 
        </div>
        <div v-else class="d-flex jc-sa">
          Click on the "Test" button to get the response
        </div>
      </div>
    </div>
  </div>
</template>

<script>

import TemplateStringEditor from "../react/TemplateStringEditor.jsx";
import api from "../../api";
import obj from "@/util/obj";
import func from '@/util/func'
import IconMenu from '../../../../shared/components/IconMenu'
import { v4 as uuidv4 } from 'uuid';

export default {
    name: "LoginStepBuilder",
    components: {
      'template-string-editor' : TemplateStringEditor,
      'icon-menu' : IconMenu
    },
    props: {
      tabName: obj.strR,
      tabData: obj.objR,
      finishedWebhookSetup: obj.boolR,
      disableOtpSave: obj.boolR
    },
    data () {
      return {
        defaultUrl: "https://juice-shop.herokuapp.com/rest/user/login",
        defaultQueryParams: "",
        defaultMethod: "POST",
        defaultHeaderString: "{'content-type': 'application/json'}",
        defaultBody: "{\"email\": \"sdf@gmail.com\", \"password\": \"qw@12345\"}",
        defaultAuthKey: "",
        defaultAuthTokenPath: "",
        stepData: [],
        showAuthParams: false,
        authParamData: [],
        showLoginSaveOption: false,
        showOtpForm: this.tabData.data ? this.tabData.data.type == "OTP_VERIFICATION" : false,
        dropDownItems: [
          {
              label: "OTP VERIFICATION",
              click: () => this.toggleShowOtpForm("OTP_VERIFICATION")
          },
          {
              label: "LOGIN FORM",
              click:() => this.toggleShowOtpForm("LOGIN_FORM")
          }
        ],
        stepType: this.tabData.data ? this.tabData.data.type : "LOGIN_FORM",
        webhookUrl: "",
        defaultRegex: "",
        otpRefUuid: ""
      }
    },
    methods: {
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
            this.onChange("headers", newData)
        },
        onChangeBody(newData) {
            this.onChange("body", newData)
        },
        onChangeAuthKey(newData) {
            this.onChange("authKey", newData)
        },
        onChangeAuthTokenPath(newData) {
            this.onChange("authTokenPath", newData)
        },
        onChangeRegex(newData) {
            let parts = newData.split('\\');
            let output = parts.join('\\');
            this.onChange("regex", output)
        },
        onChange(key, newData) {
            this.updatedData[key] = newData
            console.log(key, newData)
        },
        testSingleStep() {
          this.$emit('testSingleStep', this.updatedData, this.tabName) 
        },
        toggleShowOtpForm(stepType) {
          this.stepType = stepType
          this.showOtpForm = (stepType == "OTP_VERIFICATION" ? true: false)
        },
        webhookUrlGenerator() {
          let uuid = uuidv4();
          this.otpRefUuid = '5e1aaeff-115a-4c36-8026-2fa3e552b106'
          this.webhookUrl = window.location.origin + "/api/fetchOtpData/5e1aaeff-115a-4c36-8026-2fa3e552b106"
        },
        pollOtpResponse() {
          this.$emit('pollOtpResponse', this.webhookUrl, this.tabName)
        },
        testRegex() {
          let data = {"regex": this.updatedData['regex'], "otpRefUuid": this.otpRefUuid, "type": this.stepType}
          this.$emit('testRegex', this.tabName, data)
        }
    },
      computed: {
          updatedData() {
              this.showOtpForm;
              if (this.tabData.data) return {...this.tabData.data}
              if (this.showOtpForm) {
                return {
                  "url": this.webhookUrl,
                  "queryParams": "",
                  "method": "POST",
                  "type": this.stepType,
                  "regex": this.defaultRegex
                }

              } else {
                return {
                    "url": this.defaultUrl,
                    "queryParams": this.defaultQueryParams,
                    "method": this.defaultMethod,
                    "headers": this.defaultHeaderString,
                    "body": this.defaultBody,
                    "authKey": this.defaultAuthKey,
                    "authTokenPath": this.defaultAuthTokenPath,
                    "type": this.stepType,
                    "regex": this.defaultRegex
                }
              }
          },
          hasResponseData() {
            return this.tabData.responseHeaders != null || this.tabData.responsePayload != null
          }
      },
      mounted() {
        this.webhookUrlGenerator()
      }
    
}
</script>

<style lang="sass">
.request-editor 
  overflow-wrap: anywhere !important

.response-data 
  color: #47466A99
  font-size: 14px
  margin: auto
  overflow-y: scroll
  height: 400px

.login-builder-container
  display: flex
  overflow: scroll

.login-builder-container > div
  flex: 0 0 50%


</style>