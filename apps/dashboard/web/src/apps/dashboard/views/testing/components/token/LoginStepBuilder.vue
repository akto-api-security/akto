<template>
  <div class="pt-2">
    <div class="login-builder-container">
    
      <div>
        <div style="display: flex; padding-bottom: 24px;">
          <div style="width: 100%; position: relative">

          <div class="top-right-btn">
            <icon-menu v-if="!this.tabData.data" icon="$fas_cog" :items="dropDownItems"/>
          </div>
          

          <div :style='this.showOtpForm ? "" : "display: none"'>
              <div class="request-title">Steps to setup webhook</div>
              <div class="steps">
                  <b>Step 1</b>: Connect with zapier to send data to the following webhook url - <span style="text-decoration: underline"> {{this.webhookUrl}} </span>
              </div>
              <div class="steps">
                  <b>Step 2</b>: After finishing setup, click on "Webhook Setup Done" button for fetching OTP content
              </div>
              <div class="steps">
                  <b>Step 3</b>: Specify a regex for extracting the OTP code from the content
              </div>
              <div class="steps">
                  <b>Step 4</b>: Verify the extracted OTP value and click on "SAVE"
              </div>

              <div class="primary--text fs-12" >
                
              </div>

              <v-btn dark outlined primary color="var(--themeColor)" @click="pollOtpResponse" class="mt-2">
                  Zapier Setup Done
              </v-btn>

               <div class="request-title mt-3">Regex</div>
                  <template-string-editor
                    :defaultText="this.updatedData['regex']"
                    :onChange=onChangeRegex
                  />

                <div :disabled="disableOtpSave" class="request-title">Extracted Otp : <b>{{tabData.responsePayload ? tabData.responsePayload.otp : ''}} </b></div>
                 
              <v-btn :disabled="!finishedWebhookSetup" dark outlined primary color="var(--themeColor)" @click="testRegex"  class="mt-2">
                  Test Regex
              </v-btn>

              <v-btn :disabled="disableOtpSave" dark outlined primary color="var(--themeColor)" @click="testSingleStep"   class="mt-2 ml-2">
                Save
             </v-btn>
            
          </div>

          <div :style='this.showRecordedFlow ? "" : "display: none"'>

            <div class="d-flex">
              <div class="request-title mr-2">Upload recording file here</div>
              <div>
                <upload-file 
                  fileFormat=".json" 
                  @fileChanged="handleFileChange" 
                  tooltipText="Upload recording (.json)" 
                  label="" 
                  type="uploadRecordedLoginFlow"
                />              
              </div>
            </div>
            
            <div class="request-title mt-3">Token Fetch Command</div>
                  <template-string-editor
                    :defaultText="this.updatedData['tokenFetchCommand']"
                    :onChange=onChangeTokenFetchCommand
                  />

            <div class="request-title">Steps To Add Recording</div>
              <div class="steps">
                  <b>Step 1</b>: Please Open your browser and click on open chrome dev tools
              </div>
              <div class="steps">
                  <b>Step 2</b>: Open recorder tab and click on "Start a New Recording"
              </div>
              <div class="steps">
                  <b>Step 3</b>: Run the complete login flow, and stop the recording once done.
              </div>
              <div class="steps">
                  <b>Step 4</b>: Click on the download icon just above, and select "Export as a json file"
              </div>

              <div class="steps">
                  <b>Step 4</b>: Go to akto dashboard and specify the token fetch command, which will be used by AKTO to extract the token
              </div>

              <div class="steps">
                  <b>Step 5</b>: Specify the json script in AKTO Dashboard, and wait for couple of minutes for verifying the extracted token
              </div>
        

          </div>
          

          <div  :style='this.showOtpForm || this.showRecordedFlow ? "display: none" : ""'>

            <div class="url-form-container">
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

            <v-btn dark outlined primary color="var(--themeColor)" @click="testSingleStep" class="mt-8">
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
import UploadFile from '@/apps/dashboard/shared/components/UploadFile'

export default {
    name: "LoginStepBuilder",
    components: {
      'template-string-editor' : TemplateStringEditor,
      'icon-menu' : IconMenu,
      'upload-file': UploadFile
    },
    props: {
      tabName: obj.strR,
      tabData: obj.objR, 
      finishedWebhookSetup: obj.boolR,
      disableOtpSave: obj.boolR
    },
    data () {

      let defaults = {
        defaultUrl: "https://xyz.com",
        defaultQueryParams: "",
        defaultMethod: "POST",
        defaultHeaderString: "{'content-type': 'application/json'}",
        defaultBody: "{\"email\": \"abc@mail.com\"}",
        defaultAuthKey: "",
        defaultAuthTokenPath: "",
        defaultTokenFetchCommand: "\"Bearer \" + JSON.parse(Object.values(window.localStorage).find(x => x.indexOf(\"access_token\")> -1)).body.access_token"
      }

      let updatedData = this.tabData.data
      let showOtpForm = this.tabData.data ? this.tabData.data.type == "OTP_VERIFICATION" : false
      let showRecordedFlow = this.tabData.data ? this.tabData.data.type == "RECORDED_FLOW" : false
      let fileUploaded = false

      if (!updatedData) {
        
        updatedData = {
            "url": defaults.defaultUrl,
            "queryParams": defaults.defaultQueryParams,
            "method": defaults.defaultMethod,
            "headers": defaults.defaultHeaderString,
            "body": defaults.defaultBody,
            "authKey": defaults.defaultAuthKey,
            "authTokenPath": defaults.defaultAuthTokenPath,
            "type": "LOGIN_FORM",
            "otpRefUuid": "",
            "regex": "(\\d+){1,6}",
            "tokenFetchCommand": defaults.defaultTokenFetchCommand
        }
        
      }


      return {
        ...defaults,
        showOtpForm,
        showRecordedFlow,
        updatedData,
        fileUploaded,
        dropDownItems: [
          {
              label: "Call API",
              click:() => this.toggleShowOtpForm("LOGIN_FORM")
          },
          {
              label: "Receive OTP",
              click: () => this.toggleShowOtpForm("OTP_VERIFICATION")
          },
          {
              label: "Upload Json Recording",
              click: () => this.toggleShowOtpForm("RECORDED_FLOW")
          }
        ],
        stepType: this.tabData.data ? this.tabData.data.type : "LOGIN_FORM",
        otpRefUuid: this.tabData.data ? this.tabData.data.otpRefUuid : uuidv4(),
        webhookUrl: window.location.origin + "/saveOtpData/" + this.otpRefUuid, 
        defaultRegex: this.tabData.data ? this.tabData.data.regex : "(\d+){1,6}"
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
        onChangeTokenFetchCommand(newData) {
            this.onChange("tokenFetchCommand", newData)
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
          this.updatedData.type = stepType
          this.showOtpForm = stepType == "OTP_VERIFICATION"
          this.showRecordedFlow = stepType == "RECORDED_FLOW"

          if (this.showOtpForm) {
            this.updatedData.url = this.webhookUrl
          } else {
            this.updatedData.url = this.tabData.data ? this.tabData.data.url : this.defaultUrl
          }
        },
        webhookUrlGenerator() {
          this.updatedData.otpRefUuid = this.otpRefUuid
          this.webhookUrl = window.location.origin + "/saveOtpData/" + this.otpRefUuid
        },
        pollOtpResponse() {
          let fetchUrl = window.location.origin + "/api/fetchOtpData/" + this.otpRefUuid
          this.$emit('pollOtpResponse', fetchUrl, this.tabName)
        },
        testRegex() {
          let data = {"regex": this.updatedData['regex'], "otpRefUuid": this.otpRefUuid, "type": this.stepType}
          this.$emit('testRegex', this.tabName, data)
        },
        calcUpdatedData(defaults) {
        },
        handleFileChange({file}) {
            var reader = new FileReader();    
            reader.readAsText(file)
            var success = false;
            let callbackFunc = (success) => {
              console.log(success)
              if(success) {
                let data = {"type": this.stepType, "tokenFetchCommand": this.updatedData.tokenFetchCommand}
                this.$emit('triggerRecordedFlowPolling', data)
              }
            }
            reader.onload = () => {
                let result = api.uploadRecordedLoginFlow(reader.result, this.updatedData.tokenFetchCommand)
                result.then((resp) => {
                    console.log("recording uploaded")
                    success = true
                    callbackFunc(success)
                }).catch((err) => {
                    console.log("recording upload failed ", err)
                })
            }
        }
    },
      computed: {
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
  color: var(--themeColorDark7)
  font-size: 14px
  margin: auto
  overflow-y: scroll
  height: 400px

.login-builder-container
  display: flex
  overflow: scroll
  gap: 40px

.login-builder-container > div
  flex: 1 0 40%

.input-style
  text-decoration: none !important
  outline: none
  border: none
  border-bottom: none!important
  color: red!important

.url-form-container
  overflow-y: scroll
  max-height: 380px
  padding-bottom: 5px


.top-right-btn
  position: absolute
  top: 10px
  right: 0px  

.steps
    margin-top: 6px
    color: var(--themeColorDark)
    font-size: 13px


</style>