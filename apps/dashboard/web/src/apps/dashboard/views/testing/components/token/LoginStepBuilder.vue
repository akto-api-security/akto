<template>
  <div style="overflow:scroll" class="d-flex">
    <div>
      <div style="display: flex; padding-bottom: 24px;">
        <div style="width: 100%">

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

      <div style="height: 24px"></div>

      <div class="d-flex ma-2">
          <v-btn primary color="#6200EA" @click="testLoginStep">
              Test
          </v-btn>
          <v-btn :disabled="!tabData.showAddStepOption" primary color="#6200EA" @click="saveStepData">
              Save and add step
          </v-btn>
          <v-btn primary color="#6200EA" @click="emitRemoveTab" >
              Remove Step
          </v-btn>
          <v-btn :disabled="!tabData.testedSuccessfully" primary color="#6200EA" @click="toggleShowAuthParams" >
              Done
          </v-btn>

      </div>

      <div v-if="showAuthParams">
            <div v-for="(key, index) in authParamsList">
              <div class="input-value d-flex">
                  <v-text-field 
                      label="Auth header key"
                      style="width: 200px"
                      v-model="authParamsList[index].key"
                  />

                  <v-text-field 
                      label="Auth header value"
                      style="width: 200px"
                      v-model="authParamsList[index].value"
                  />       

                  <v-btn primary plain color="#6200EA" @click="deleteAuthElem(index)" >
                      Delete
                  </v-btn>

              </div>
            </div>
            <v-btn primary plain color="#6200EA" @click='addNewAuthParamElem' >
                Add
            </v-btn>
            <v-btn primary plain color="#6200EA" @click='toggleSaveLoginStep' >
                Done
            </v-btn>

      </div>

      <div class="d-flex">
          <v-btn :disabled="!showLoginSaveOption || !tabData.testedSuccessfully" primary plain color="#6200EA" @click="saveLoginStep" >
              Save
          </v-btn>
      </div>

      </div>

    <div style="width: 400px; opacity: 0.5">
          <div className="request-title">[Response] Headers</div>
          <div className="request-editor request-editor-headers">
            {{tabData.responseHeaders}}
          </div>
          <div className="request-title">[Response] Payload</div>
          <div className="request-editor request-editor-payload">
            {{tabData.responsePayload}}
          </div>
        </div>

  </div>
</template>

<script>

import TemplateStringEditor from "../react/TemplateStringEditor.jsx";
import api from "../../api";
import obj from "@/util/obj";
import func from '@/util/func'

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
      console.log("data: " , this.tabName);
      return {
        defaultUrl: "http://juice-appli-1fi2t5cedjkey-1701018947.ap-south-1.elb.amazonaws.com/rest/user/login",
        defaultQueryParams: "",
        defaultMethod: "POST",
        defaultHeaderString: "{'content-type': 'application/json'}",
        defaultBody: "{\"email\": \"sdf@gmail.com\", \"password\": \"qw@12345\"}",
        defaultAuthKey: "",
        defaultAuthTokenPath: "",
        stepData: [],
        showAuthParams: false,
        authParamData: [],
        authParamsList: [{key: "", "where": "HEADER", value: ""}],
        showLoginSaveOption: false
      }
    },
    methods: {
        addNewAuthParamElem() {
          let authParamClone = [...this.authParamsList]
          authParamClone.push({key: "", "where": "HEADER", value:""})
          this.authParamsList = authParamClone
        },
        emitRemoveTab() {
          console.log('logging tabname')
          console.log(this.tabName)
          this.$emit('removeTab', this.tabName)
        },
        emitAddTab() {
          console.log('logging add tabname')
          console.log(this.tabName)
          this.$emit('addTab', this.updatedData, this.tabName)
        },
        onChangeURL(newData) {
            console.log('url changed')
            console.log(newData)
            this.onChange("url", newData)
        },
        onChangeMethod(newData) {
            console.log('method changed')
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
        onChange(key, newData) {
            this.updatedData[key] = newData
            this.tabData.testedSuccessfully = false
        },
        toggleShowAuthParams() {
          this.showAuthParams = true
          this.$emit('saveTabInfo', this.updatedData, this.tabName)
        },
        testLoginStep() {
          console.log('login test logs')
          console.log(this.updatedData)
          console.log('login test logs2')
          console.log(this.tabName)
          this.$emit('testLoginStep', this.updatedData, this.tabName)
          console.log('emit test event')
        },
        toggleSaveLoginStep() {
          this.showLoginSaveOption = true
        },
      saveStepData() {
        console.log("stepData log")
        console.log(JSON.stringify(this.stepData))
        let data = this.stepData
        data.push(JSON.stringify(this.updatedData))
        this.stepData = data
        console.log("stepData log2")
        console.log(this.stepData)
        this.emitAddTab()
      },
      saveLoginStep() {
          this.$emit('saveLoginStep', this.authParamsList)
      },
      deleteAuthElem(item) {
        console.log("delete auth")
        this.authParamsList.splice(item, 1)
        console.log(item)
      }
    },
      computed: {
          updatedData() {
              if (this.originalDbState) return {...this.originalDbState}
              return {
                  "url": this.defaultUrl,
                  "queryParams": this.defaultQueryParams,
                  "method": this.defaultMethod,
                  "headers": this.defaultHeaderString,
                  "body": this.defaultBody,
                  "authKey": this.defaultAuthKey,
                  "authTokenPath": this.defaultAuthTokenPath
              }
          },
        urlAndMethodFilled() {
            return this.updatedData["url"]
          }
      }
    
}
</script>

<style lang="sass">
.request-title
  padding-top: 12px
  font-size: 14px
  color: #47466A
  font-family: 'Poppins'
  font-weight: 500

.request-editor 
  font-size: 14px !important
  word-wrap: break-word !important

</style>