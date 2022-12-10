<template>
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

    <div class="d-flex ma-2">
        <v-btn primary color="#6200EA" @click="testLoginStep">
            Test
        </v-btn>
        <v-btn primary color="#6200EA" @click="saveStepData" :disabled="!testedSuccessfully">
            Save and add step
        </v-btn>
        <v-btn primary plain color="#6200EA" @click="emitRemoveTab" >
            Remove Step
        </v-btn>
        <v-btn primary plain color="#6200EA" @click="toggleShowAuthParams" >
            Done
        </v-btn>

    </div>

    <div class="d-flex" v-if="showAuthParams">
            <div class="input-value">
                <v-text-field 
                    label="Auth header key"
                    style="width: 200px"
                />
            </div>
            <div class="input-value">
                <v-text-field 
                    label="Auth header value"
                    style="width: 500px"
                />                    
            </div>
    </div>

    <div class="d-flex">
        <v-btn primary plain color="#6200EA" @click="saveLoginStep" >
            Save
        </v-btn>
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
      tabName: obj.strR
    },
    data () {
      return {
        defaultUrl: "",
        defaultQueryParams: "",
        defaultMethod: "POST",
        defaultHeaderString: "{'content-type': 'application/json'}",
        defaultBody: "{}",
        defaultAuthKey: "",
        defaultAuthTokenPath: "",
        stepData: [],
        testedSuccessfully: false,
        showAuthParams: false,
        authParamData: []
      }
    },
    props: {
      originalDbState: obj.objN
    },
    methods: {
        emitRemoveTab() {
          this.$emit('removeTab', this.tabName)
        },
        emitAddTab() {
          this.$emit('addTab', this.tabName)
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
            this.onChange("headerString", newData)
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
            this.testedSuccessfully = false
        },
        toggleShowAuthParams() {
          this.showAuthParams = true
        },
        testLoginStep() {
        
          let reqData = this.stepData
          reqData.push(this.updatedData)
          let result = api.triggerLoginSteps("LOGIN_REQUEST", reqData, [])

          result.then((resp) => {
              this.showLoginSaveOption = true
              console.log('success')
              func.showSuccessSnackBar("Login flow ran successfully!")
              this.testedSuccessfully = true
          }).catch((err) => {
              this.showLoginSaveOption = false
              console.log(err);
          })

          //this.$emit("testLoginStep", {"updatedData": this.stepData})


      },
      saveStepData() {
        this.stepData.push(this.updatedData)
        this.emitAddTab()
      },
      saveLoginStep() {
          
          let result = api.addAuthMechanism("LOGIN_REQUEST", this.stepData, this.authParamData)

          result.then((resp) => {
              func.showSuccessSnackBar("Login Flow saved successfully!")
          }).catch((err) => {
              console.log(err);
          })

      }
    },
      computed: {
          updatedData() {
              if (this.originalDbState) return {...this.originalDbState}
              return {
                  "url": this.defaultUrl,
                  "queryParams": this.defaultQueryParams,
                  "method": this.defaultMethod,
                  "headerString": this.defaultHeaderString,
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

</style>