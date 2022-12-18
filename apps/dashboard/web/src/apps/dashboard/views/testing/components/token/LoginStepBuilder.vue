<template>
  <div class="pt-2">
    <div class="login-builder-container">
    
      <div>
        <div style="display: flex; padding-bottom: 24px;">
          <div style="width: 100%">


          <v-btn primary plain color="#6200EA" @click='toggleShowOtpForm' >
                      Toggle
                  </v-btn>

          <div v-if="this.showOtpForm">

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
                
               <div class="request-title">Regex</div>
                  <template-string-editor
                    :defaultText="this.updatedData['regex']"
                    :onChange=onChangeRegex
                  />
            
          </div>

          <div v-else>

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
              :defaultText="this.updatedData['headers']"
              :onChange=onChangeHeaders
            />

            <div class="request-title">Body</div>
            <template-string-editor
              :defaultText="this.updatedData['body']"
              :onChange=onChangeBody
            />

          </div>

          </div>  
        </div>


        <v-btn dark outlined primary color="#6200EA" @click="testLoginStep">
            Test
        </v-btn>
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
import store from "@/apps/main/store/module";

export default {
    name: "LoginStepBuilder",
    components: {
      'template-string-editor' : TemplateStringEditor
    },
    props: {
      tabName: obj.strR,
      tabData: obj.objR
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
        showOtpForm: false,
        dropDownItems: [
          {
              label: "OTP VERIFICATION",
              click: this.toggleShowOtpForm
          }
        ],
        stepType: "LOGIN_FORM",
        webhookUrl: "",
        regex: ""
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
            this.onChange("regex", newData)
        },
        onChange(key, newData) {
            this.updatedData[key] = newData
            this.tabData.testedSuccessfully = false
        },
        testLoginStep() {
          this.$emit('testLoginStep', this.updatedData, this.tabName)
        },
        toggleShowOtpForm() {
          this.showOtpForm = !this.showOtpForm
          if(this.showOtpForm) {
            this.stepType = "OTP_VERIFICATION"
          } else {
            this.stepType = "LOGIN_FORM"
          }
        },
        webhookUrlGenerator() {
          let uuid = uuidv4();
          this.webhookUrl = window.location.origin + "/api/fetchOtpData/" + uuid
        }
    },
      computed: {
          updatedData() {
              if (this.tabData.data) return {...this.tabData.data}
              if (this.showOtpForm) {
                let body = {"regex": this.regex}
                let headers = {
                  "Content-Type": "application/json",
                  "access-token": store.getters["auth/getAccessToken"]
                }
                return {
                  "url": this.webhookUrl,
                  "queryParams": "",
                  "method": "POST",
                  "type": this.stepType,
                  "body": JSON.stringify(body),
                  "headers": JSON.stringify(headers)
                }

              }
              return {
                  "url": this.defaultUrl,
                  "queryParams": this.defaultQueryParams,
                  "method": this.defaultMethod,
                  "headers": this.defaultHeaderString,
                  "body": this.defaultBody,
                  "authKey": this.defaultAuthKey,
                  "authTokenPath": this.defaultAuthTokenPath,
                  "type": this.stepType,
                  "regex": this.regex
              }
          },
          hasResponseData() {
            return this.tabData.responseHeaders != null || this.tabData.responsePayload != null
          },
        urlAndMethodFilled() {
            return this.updatedData["url"]
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