<template>
    <div class="pa-4" style="background-color: #FFFFFF">
        <layout-with-tabs :tabs='apiTabs' :defaultTabName='currTabName'>
            <template v-for="(apiTabName, index) in apiTabs" v-slot:[apiTabName]="slotprops">
                <div :key="index" class="fd-column">
                    <div style="height: 500px" class="d-flex">
                        <login-step-builder
                            :tabName="apiTabName"
                            :tabData="stepData[apiTabName]"
                            @addTab=addTab 
                            @removeTab=removeTab 
                            @testLoginStep=testLoginStep
                            @saveLoginStep=saveLoginStep
                            @saveTabInfo=saveTabInfo
                        />
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
    },
    data() {
        return {
            apiTabs: ["API-1"],
            counter: 1,
            apiTabsInfo: {},
            currTabName: "API-1",
            stepData: {
                "API-1": {
                    "showAddStepOption": false, 
                    "testedSuccessfully": false
                }
            },
            showLoginSaveOption: false,
            addNewDataToTestReq: true
        }
    },
    methods: {
        testLoginStep(updatedData, tabString) {
            
          console.log("test login log")
          console.log(JSON.stringify(updatedData))
          console.log(tabString)
          console.log(JSON.stringify(this.stepData))

          let reqData = []

          for (let key in this.stepData) {
            console.log(key, this.stepData[key]);
            
            if (this.stepData[key]["data"]==null) {
                continue
            }
            reqData.push(this.stepData[key]["data"]) 
          }

          if (this.addNewDataToTestReq) {
            reqData.push(updatedData) 
          }
        //   console.log("test login log2")
        //   console.log(JSON.stringify(updatedData))
        //   console.log(JSON.stringify(reqData))
          let result = api.triggerLoginSteps("LOGIN_REQUEST", reqData, [])

           result.then((resp) => {
              this.showLoginSaveOption = true
              console.log('success')
              func.showSuccessSnackBar("Login flow ran successfully!")
              let index = 0;

              for (let key in this.stepData) {
                console.log(key, this.stepData[key]);
                
                // if (this.stepData[key]["data"]==null) {
                //     break
                // }
                let r = this.stepData[key]
                let respData = resp.responses[index]
                let myobj = JSON.parse(respData);
                r.responseHeaders = myobj.headers
                r.responsePayload = myobj.body
                if (key == tabString) {
                    r.showAddStepOption = true
                    r.testedSuccessfully = true
                }
                this.stepData[key] = r 
                index++
            }    

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
        saveLoginStep(authParamsList) {
          
          let reqData = []
          for (let key in this.stepData) {
            console.log(key, this.stepData[key]);
            
            if (this.stepData[key]["data"]==null) {
                continue
            }
            reqData.push(this.stepData[key]["data"]) 
          }

          let result = api.addAuthMechanism("LOGIN_REQUEST", reqData, authParamsList)

          result.then((resp) => {
              func.showSuccessSnackBar("Login Flow saved successfully!")
          }).catch((err) => {
              console.log(err);
          })

        },
        addTab(updatedData, tabString) {
            console.log('tabString')
            console.log(tabString)
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
        saveTabInfo(updatedData, tabString) {
            this.stepData[tabString] = {"data": updatedData, "showAddStepOption": false,
             "testedSuccessfully": true}
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

</style>