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

        <div class="request-title">Auth header key</div>
        <template-string-editor 
          :defaultText="this.updatedData['authKey']"
          :onChange=onChangeAuthKey
        />

        <div class="request-title">Auth header response key name</div>
        <template-string-editor 
          :defaultText="this.updatedData['authTokenPath']"
          :onChange=onChangeAuthTokenPath
        />

      </div>  
    </div>

    <div style="height: 24px"></div>

    <div class="d-flex ma-2">
        <v-btn class = "mx-2" primary dark color="#6200EA" @click="testLoginStep">
            Test
        </v-btn>
        <v-btn v-if="showLoginSaveOption" primary dark color="#6200EA" @click="saveLoginStep">
            Save
        </v-btn>
    </div>
  </div>
</template>

<script>

import TemplateStringEditor from "./components/react/TemplateStringEditor.jsx";
import api from "./api";
import obj from "@/util/obj";

export default {
    name: "LoginStepBuilder",
    components: {
      'template-string-editor' : TemplateStringEditor
    },
    data () {
      return {
        defaultUrl: "",
        defaultQueryParams: "",
        defaultMethod: "POST",
        defaultHeaderString: "{'content-type': 'application/json'}",
        defaultBody: "{}",
        defaultAuthKey: "",
        defaultAuthTokenPath: ""
      }
    },
    props: {
      showLoginSaveOption: obj.boolR
    },
    methods: {
        testLoginStep() {
            console.log('test event')
            console.log(this.updatedData)
            this.$emit("testLoginStep", {"updatedData": this.updatedData})
        },
        saveLoginStep() {
            console.log('save event')
            this.$emit("saveLoginStep", {"updatedData": this.updatedData})
        },
        onChangeURL(newData) {
            console.log('url changed')
            console.log(newData)
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
        onChangeAuthKey(newData) {
            this.onChange("authKey", newData)
        },
        onChangeAuthTokenPath(newData) {
            this.onChange("authTokenPath", newData)
        },
        onChange(key, newData) {
            this.updatedData[key] = newData
        }
    },
    computed: {
        updatedData() {
            return {
                "url": this.defaultUrl,
                "queryParams": this.defaultQueryParams,
                "method": this.defaultMethod,
                "headerString": this.defaultHeaderString,
                "body": this.defaultBody,
                "authKey": this.defaultAuthKey,
                "authTokenPath": this.defaultAuthTokenPath
            }
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