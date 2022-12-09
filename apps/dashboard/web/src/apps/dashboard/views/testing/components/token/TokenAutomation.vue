<template>
    <div class="pa-4" style="background-color: #FFFFFF">
        <layout-with-tabs :tabs='apiTabs' :defaultTabName='currTabName'>
            <template v-for="(key, index) in apiTabs" v-slot:[key]="">
                <div :key="index" class="fd-column">
                    <div style="height: 500px" class="d-flex">
                        <login-step-builder
                            :tabName="key"
                            @addTab=addTab 
                            @removeTab=removeTab 
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

import {mapState} from 'vuex'
import LayoutWithTabs from '@/apps/dashboard/layouts/LayoutWithTabs'
import LoginStepBuilder from './LoginStepBuilder'

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
            currTabName: "API-1"
        }
    },
    methods: {
        addTab() {
            this.counter++
            let newTabName = "API-"+this.counter
            this.apiTabs.push(newTabName)
            this.currTabName = newTabName
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
            this.currTabName = this.apiTabs[Math.min(0, indexTab-1)]
            this.apiTabs.splice(indexTab, 1)
        }

    },
    computed: {

    }
}
</script>

<style lang="sass" scoped>

</style>