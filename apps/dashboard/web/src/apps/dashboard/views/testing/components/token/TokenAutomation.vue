<template>
    <div class="pa-4 automation-container">
        <div v-if="showAuthParams">
              <div v-for="(key, index) in authParamsList" :key="index">
                <div class="input-value d-flex">
                    <v-text-field 
                        label="Header key"
                        style="width: 200px"
                        v-model="authParamsList[index].key"
                    />

                    <v-text-field 
                        label="Header"
                        style="width: 200px"
                        v-model="authParamsList[index].value"
                    />       

                    <v-btn primary icon color="#6200EA" @click="deleteAuthElem(index)" class="ma-auto" v-if="authParamsList.length > 1">
                        <v-icon>$fas_trash</v-icon>
                    </v-btn>

                </div>
              </div>
              <v-btn primary icon color="#6200EA" @click='toggleShowAuthParamsAuthTab' >
                    <v-icon> $fas_arrow-left </v-icon>
                </v-btn>
                <v-btn primary icon color="#6200EA" @click='addNewAuthParamElem' >
                    <v-icon> $fas_plus </v-icon>
                </v-btn>
                <v-btn primary plain color="#6200EA" @click='testLoginStep' >
                    Test
                </v-btn>
                <v-btn primary plain color="#6200EA" @click='saveLoginStep' >
                    Done
                </v-btn>

            </div>

        <layout-with-tabs :tabs='apiTabs' :defaultTabName='currTabName' v-else>
            <template v-for="(apiTabName, index) in apiTabs" v-slot:[apiTabName]>
                <div :key="index" class="fd-column">
                    <div>
                        <login-step-builder
                            :tabName="apiTabName"
                            :tabData="stepData[apiTabName]"
                            :finishedWebhookSetup="finishedWebhookSetup"
                            :disableOtpSave="disableOtpSave"
                            @testSingleStep=testSingleStep
                            @pollOtpResponse=pollOtpResponse
                            @testRegex=testRegex
                        />
                        <v-btn plain color="#6200EA" @click="removeTab(apiTabName)" class="top-right-btn">
                            Remove Step
                        </v-btn>
                        <div class="float-right ma-2">
                            <v-btn :disabled="!stepData[apiTabName].testedSuccessfully" class="token-automation-primary-btn" @click="addTab(apiTabName)"  style="background-color:  #6200EA !important; color: #FFFFFF !important">
                                Add step
                            </v-btn>
                            <v-btn :disabled="!stepData[apiTabName].showAddStepOption" class="token-automation-primary-btn" @click="toggleShowAuthParams(apiTabName)"  style="background-color:  #6200EA !important; color: #FFFFFF !important">
                                Extract
                            </v-btn>
                        </div>
                    </div>
                </div>
            </template>
        </layout-with-tabs>
    </div>
</template>

<script>
import func from '@/util/func'
import obj from '@/util/obj'
import testing from '@/util/testing'
import api from '../../api'

import {mapState} from 'vuex'
import LayoutWithTabs from '@/apps/dashboard/layouts/LayoutWithTabs'
import LoginStepBuilder from './LoginStepBuilder'
import store from "@/apps/main/store/module";

export default {
    name: "TokenAutomation",
    components: {
        LayoutWithTabs,
        LoginStepBuilder
    },
    props: {
        originalDbState: obj.objR
    },
    data() {
        let tabIndex = 0
        let tabs = []
        let stepsData = {}
        let paramList = []
        if (this.originalDbState != null) {
            this.originalDbState.requestData.forEach(function (data) {
                tabIndex++
                let key = "API-"+tabIndex
                tabs.push(key)
                stepsData[key] = {"data": data, "showAddStepOption": false,
                "testedSuccessfully": true}
            });
            paramList = this.originalDbState.authParams
        } else {
            tabs = ["API-1"]
            tabIndex++
            stepsData = {
                "API-1": {
                    "showAddStepOption": false, 
                    "testedSuccessfully": false
                }
            }
            paramList = [{key: "", "where": "HEADER", value: ""}]
        }
        
        return {
            apiTabs: tabs,
            counter: tabIndex,
            apiTabsInfo: {},
            currTabName: "API-1",
            showAuthParams: false,
            stepData: stepsData,
            showLoginSaveOption: false,
            addNewDataToTestReq: true,
            testedDataButNotSaved: null,
            authParamsList: paramList,
            finishedWebhookSetup: false,
            disableOtpSave: true
        }
    },
    methods: {
        deleteAuthElem(item) {
            this.authParamsList.splice(item, 1)
        },

        addNewAuthParamElem() {
          let authParamClone = [...this.authParamsList]
          authParamClone.push({key: "", "where": "HEADER", value:""})
          this.authParamsList = authParamClone
        },

        toggleShowAuthParams(tabName) {
          this.showAuthParams = true
        },

        toggleShowAuthParamsAuthTab() {
            this.showAuthParams = !this.showAuthParams
        },

        async pollOtpResponse(webhookUrl, tabString) {
            let pollAttempts = 20
            let pollSleepDuration = 10000
            let success = false
            let errResp;
            for (let i=0; i<pollAttempts; i++) {
                if (success) {
                    break
                } else {
                  await this.sleep((i) * pollSleepDuration);
                }
                let result = api.fetchOtpData(webhookUrl)
                result.then((resp) => {
                    console.log("polled otp text")
                    let stepDataCopy = JSON.parse(JSON.stringify(this.stepData[tabString]));
                    stepDataCopy.responsePayload = resp
                    this.$set(this.stepData, tabString, stepDataCopy);
                    success = true
                    this.finishedWebhookSetup = true
                }).catch((err) => {
                    console.log("polling otp text err")
                    errResp = err
                })
            }
            if (!success) {
                let stepDataCopy = JSON.parse(JSON.stringify(this.stepData[tabString]));
                stepDataCopy.responsePayload = errResp.response.data.actionErrors
                this.$set(this.stepData, tabString, stepDataCopy);
            }
        },
        sleep(ms) {
            return new Promise(resolve => setTimeout(resolve, ms));
        },
        testRegex(tabString, data) {
          let regexp = new RegExp(data.regex, "g");
          let matches = regexp.exec(this.stepData[tabString].responsePayload.otpText);
          if (matches == null || matches.length < 2) {
            this.disableOtpSave = true
            return
          }
          let stepDataCopy = JSON.parse(JSON.stringify(this.stepData[tabString]));
          stepDataCopy.responsePayload.otp = matches[1]
          stepDataCopy.data = data
          this.$set(this.stepData, tabString, stepDataCopy);
          this.disableOtpSave = false
        },

        testSingleStep(updatedData, tabString) {

            let indexTab = this.apiTabs.indexOf(tabString)
            let nodeId = "x" + ((indexTab * 2) + 1)
            let result = api.triggerSingleStep("LOGIN_REQUEST", nodeId, [updatedData])

            result.then((resp) => {
                func.showSuccessSnackBar("Login flow ran successfully!")

                let stepDataCopy = JSON.parse(JSON.stringify(this.stepData[tabString]));
                let respData = resp.responses[0]
                let myobj = JSON.parse(respData);
                stepDataCopy.responseHeaders = myobj.headers
                stepDataCopy.responsePayload = myobj.body
                stepDataCopy.showAddStepOption = true
                stepDataCopy.testedSuccessfully = true
                stepDataCopy.data = updatedData
                this.$set(this.stepData, tabString, stepDataCopy);

            }).catch((err) => {
                
                console.log(err);
                let errResp = err.response.data.responses
                if (errResp == null || errResp.length == 0) return

                let stepDataCopy = JSON.parse(JSON.stringify(this.stepData[tabString]));
                let respData = errResp[0]
                let myobj = JSON.parse(respData);
                stepDataCopy.responseHeaders = myobj.headers
                stepDataCopy.responsePayload = myobj.body
                stepDataCopy.showAddStepOption = false
                stepDataCopy.testedSuccessfully = false
                this.$set(this.stepData, tabString, stepDataCopy);
            })
        },
        testLoginStep() {

          let reqData = Object.values(this.stepData).filter(x => x.data != null).map(x => x.data)

          let result = api.triggerLoginSteps("LOGIN_REQUEST", reqData, this.authParamsList)

           result.then((resp) => {
              func.showSuccessSnackBar("Login flow ran successfully!")
          }).catch((err) => {
              console.log(err);
              let errResp = err.response.data.responses
              if (errResp == null || errResp.length == 0) return
              let index = 0;
              let stepDataCopyObj = {}

              for (let key in this.stepData) {
                let stepDataCopy = JSON.parse(JSON.stringify(this.stepData[key]));
                if (index < errResp.length) {
                    let respData = errResp[index]
                    let myobj = JSON.parse(respData);
                    stepDataCopy.responseHeaders = myobj.headers
                    stepDataCopy.responsePayload = myobj.body
                    stepDataCopy.showAddStepOption = false
                    stepDataCopy.testedSuccessfully = false
                } else {
                    stepDataCopy.responseHeaders = null
                    stepDataCopy.responsePayload = null
                }
                stepDataCopyObj[key] = stepDataCopy
                index++
                }
              this.stepData = Object.assign({}, this.stepData, stepDataCopyObj)
          })
        },
        saveLoginStep() {
          
          let reqData = []
          for (let key in this.stepData) {
            
            if (this.stepData[key]["data"]==null) {
                continue
            }
            reqData.push(this.stepData[key]["data"]) 
          }

          let result = api.addAuthMechanism("LOGIN_REQUEST", reqData, this.authParamsList)

          result.then((resp) => {
              func.showSuccessSnackBar("Login Flow saved successfully!")
              this.$emit('toggleOriginalStateDb')
          }).catch((err) => {
              console.log(err);
          })

          this.showAuthParams = false
          this.$emit('closeLoginStepBuilder')
        },
        addTab(tabString) {
            this.counter++
            let newTabName = "API-"+this.counter
            let apiTabsCopy = [...this.apiTabs]
            apiTabsCopy.push(newTabName)
            this.apiTabs = apiTabsCopy
            this.currTabName = newTabName
            let objData = {"showAddStepOption": false,
             "testedSuccessfully": false}

            this.$set(this.stepData, newTabName, objData)
        },
        saveTabInfo(tabString) {
            if (this.testedDataButNotSaved) {
                let updatedData = this.testedDataButNotSaved
                this.stepData[tabString] = {"data": updatedData, "showAddStepOption": false,
                "testedSuccessfully": true}
            }
        },
        removeTab (tabName) {
            this.addNewDataToTestReq = false
            if (this.apiTabs.length == 1) {
                window._AKTO.$emit('SHOW_SNACKBAR', {
                    show: true,
                    text: `Can't remove the last tab`,
                    color: 'red'
                })
                return
            }
            let indexTab = this.apiTabs.indexOf(tabName)
            this.currTabName = this.apiTabs[Math.min(0, indexTab-1)]
            this.apiTabs.splice(indexTab, 1)
            this.$delete(this.stepData, tabName)
        }
    },

    mounted () {
    },

    
    computed: {

    }
}
</script>

<style lang="sass" scoped>
.top-right-btn
  position: absolute
  top: 10px
  right: 0px  

.automation-container
    background-color: #FFFFFF 
    height: 550px
    overflow-y: scroll
    color: #47466A

.token-automation-primary-btn
    background-color: #6200EA !important
    color: #FFFFFF !important
    margin-left: 12px

    &.v-btn--disabled
        opacity: 0.3 !important

</style>