<template>
    <div class="pa-4" style="background-color: #FFFFFF; height: 550px">
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
              <v-btn primary icon color="#6200EA" @click='addNewAuthParamElem' >
                  <v-icon> $fas_plus </v-icon>
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
                            @testLoginStep=testLoginStep
                        />
                        <v-btn plain color="#6200EA" @click="removeTab(apiTabName)" class="top-right-btn">
                            Remove Step
                        </v-btn>
                        <div class="float-right ma-2">
                            <v-btn dark  primary color="#6200EA" @click="addTab(apiTabName)">
                                Next step
                            </v-btn>
                            <v-btn dark  primary color="#6200EA" @click="toggleShowAuthParams(apiTabName)" >
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
import IconMenu from '@/apps/dashboard/shared/components/IconMenu'

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
            authParamsList: paramList
        }
    },
    methods: {
        deleteAuthElem(item) {
            console.log("delete auth")
            this.authParamsList.splice(item, 1)
            console.log(item)
        },

        addNewAuthParamElem() {
          let authParamClone = [...this.authParamsList]
          authParamClone.push({key: "", "where": "HEADER", value:""})
          this.authParamsList = authParamClone
        },

        toggleShowAuthParams(tabName) {
          this.showAuthParams = true
          this.saveTabInfo(tabName)
        },

        testLoginStep(updatedData, tabString) {
          this.testedDataButNotSaved = updatedData
          console.log("test login log")
          console.log(JSON.stringify(updatedData))
          console.log(tabString)
          console.log(JSON.stringify(this.stepData))

          
          let currentTabPushed = false

          if (!this.stepData[tabString]) {
            this.stepData[tabString] = {}
          }

          this.stepData[tabString]["data"] = updatedData
          this.stepData = {...this.stepData}

          this.stepData[tabString]["data"] = updatedData


          let reqData = Object.values(this.stepData).filter(x => x.data != null).map(x => x.data)
          let result = api.triggerLoginSteps("LOGIN_REQUEST", reqData, [])

           result.then((resp) => {
              this.showLoginSaveOption = true
              console.log('success')
              func.showSuccessSnackBar("Login flow ran successfully!")
              let index = 0;

              let stepDataCopyObj = {}

              for (let key in this.stepData) {
                console.log(key, this.stepData[key]);
                
                // if (this.stepData[key]["data"]==null) {
                //     break
                // }
               //let stepDataCopy = this.stepData[key]
                let stepDataCopy = JSON.parse(JSON.stringify(this.stepData[key]));
                let respData = resp.responses[index]
                let myobj = JSON.parse(respData);
                stepDataCopy.responseHeaders = myobj.headers
                stepDataCopy.responsePayload = myobj.body
                if (key == tabString) {
                    stepDataCopy.showAddStepOption = true
                    stepDataCopy.testedSuccessfully = true
                }
                //this.$set(this.stepData, key, this.stepData[key])
                //Vue.set(this.stepData, key, stepDataCopy)
                stepDataCopyObj[key] = stepDataCopy
                index++
            }

            this.stepData = Object.assign({}, this.stepData, stepDataCopyObj)

            //   let this.stepData = Object.keys(this.stepData).sort().reduce(
            //     (obj, key) => { 
            //         obj[key] = this.stepData[key]; 
            //         return obj;
            //     }, 
            //     {}
            //   );      
          }).catch((err) => {
              this.showLoginSaveOption = false
              this.testedSuccessfully = false
              let r = this.stepData[tabString]
              r.showAddStepOption = false
              r.testedSuccessfully = false
              this.stepData[tabString].showAddStepOption = false
              console.log(err);
          })
        },
        saveLoginStep() {
          
          let reqData = []
          for (let key in this.stepData) {
            console.log(key, this.stepData[key]);
            
            if (this.stepData[key]["data"]==null) {
                continue
            }
            reqData.push(this.stepData[key]["data"]) 
          }

          let result = api.addAuthMechanism("LOGIN_REQUEST", reqData, this.authParamsList)

          result.then((resp) => {
              func.showSuccessSnackBar("Login Flow saved successfully!")
          }).catch((err) => {
              console.log(err);
          })

          this.showAuthParams = false
          this.$emit('closeLoginStepBuilder')
        },
        addTab(tabString) {
            console.log('tabString')
            console.log(tabString)
            let updatedData = this.testedDataButNotSaved
            this.addNewDataToTestReq = true
            this.stepData[tabString] = {"data": updatedData, "showAddStepOption": false,
             "testedSuccessfully": true}
            this.counter++
            let newTabName = "API-"+this.counter
            let apiTabsCopy = [...this.apiTabs]
            apiTabsCopy.push(newTabName)
            this.apiTabs = apiTabsCopy
            this.currTabName = newTabName

            console.log('add tab data')
            console.log(JSON.stringify(updatedData))
            console.log(JSON.stringify(this.stepData))

            let objData = {"showAddStepOption": false,
             "testedSuccessfully": false}

            this.$set(this.stepData, newTabName, objData)

            // let reqData = this.stepData
            // this.stepData.push(updatedData)
            // this.stepData = reqData
            console.log(JSON.stringify(this.stepData))
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
            console.log(indexTab)
            this.currTabName = this.apiTabs[Math.min(0, indexTab-1)]
            //this.apiTabs = Array.from({length: this.apiTabs.length -1}, (_, i) => this.apiTabs[i >= indexTab ? i + 1: i]);
            this.apiTabs.splice(indexTab, 1)
            Vue.delete(this.stepData, tabName);
            //this.stepData.splice(indexTab, 1)
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

</style>