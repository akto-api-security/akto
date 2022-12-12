<template>
    <div class="pa-4" style="background-color: #FFFFFF">
        <layout-with-tabs :tabs='apiTabs' :defaultTabName='currTabName'>
            <template v-for="(key, index) in apiTabs" v-slot:[key]="slotprops">
                <div :key="index" class="fd-column">
                    <div style="height: 500px" class="d-flex">
                        <span>{{key}} {{slotprops}}</span>
                        <login-step-builder
                            :tabName="key"
                            :showAddStepOption="showAddStepOption"
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
            stepData: [],
            showAddStepOption: false,
            showLoginSaveOption: false
        }
    },
    methods: {
        testLoginStep(updatedData) {
            
          console.log("test login log")
          console.log(JSON.stringify(this.stepData))
          let reqData = this.stepData.slice()
          reqData.push(updatedData)
          console.log("test login log2")
          console.log(JSON.stringify(updatedData))
          console.log(JSON.stringify(reqData))
          let result = api.triggerLoginSteps("LOGIN_REQUEST", reqData, [])

          result.then((resp) => {
              this.showLoginSaveOption = true
              console.log('success')
              func.showSuccessSnackBar("Login flow ran successfully!")
              this.showAddStepOption = true
          }).catch((err) => {
              this.showLoginSaveOption = false
              this.showAddStepOption = false
              console.log(err);
          })
        },
        saveLoginStep(authParamsList) {
          
          let result = api.addAuthMechanism("LOGIN_REQUEST", this.stepData, authParamsList)

          result.then((resp) => {
              func.showSuccessSnackBar("Login Flow saved successfully!")
          }).catch((err) => {
              console.log(err);
          })

        },
        addTab(updatedData) {
            this.counter++
            let newTabName = "API-"+this.counter
            let apiTabsCopy = [...this.apiTabs]
            apiTabsCopy.push(newTabName)
            this.apiTabs = apiTabsCopy
            this.currTabName = newTabName

            console.log('add tab data')
            console.log(JSON.stringify(updatedData))
            console.log(JSON.stringify(this.stepData))
            let reqData = this.stepData
            this.stepData.push(updatedData)
            this.stepData = reqData
            console.log(JSON.stringify(this.stepData))
        },
        saveTabInfo(updatedData) {
            let reqData = this.stepData
            this.stepData.push(updatedData)
            this.stepData = reqData
        },
        removeTab (tabName) {
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
            this.apiTabs.splice(indexTab, 1)
            this.stepData.splice(indexTab, 1)
        }
    },
    
    computed: {

    }
}
</script>

<style lang="sass" scoped>

</style>