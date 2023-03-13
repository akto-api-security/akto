<template>
    <div>
        Use burp plugin to send traffic to Akto and realize quick value. If you like what you see,
        we highly recommend using AWS or GCP traffic mirroring to get real user data for a smooth, automated and minimum
        false positive experience.
        <div class="container mt-3">
            <div class="row">
                    <div class="col_1">
                        <p> 1 </p>
                    </div>
                    <div style="margin-left: 4px">
                        <span style="text-decoration: underline; cursor: pointer" @click="downloadBurpJar">Download</span>
                        Akto's Burp extension
                        <span v-if="this.downloadLoading === 1" style="padding-left: 4px">
                            <v-progress-circular indeterminate color="grey" :size="12" :width="1.5"></v-progress-circular>
                        </span>
                        <span v-if="this.downloadLoading === 2">
                            <v-icon color="green">$fas_check-circle</v-icon>
                        </span>
                    </div>
            </div>

            <div class="row">
                    <div class="col_1">
                        <p> 2 </p>
                    </div>
                    <div style="margin-left: 4px">
                        Open Burp and add the downloaded jar file in extension tab
                    </div>
                    <span v-if="this.sendInitialDataLoading === 1" style="padding-left: 4px">
                        <v-progress-circular indeterminate color="grey" :size="12" :width="1.5"></v-progress-circular>
                    </span>
                    <span v-if="this.sendInitialDataLoading === 2">
                        <v-icon color="green">$fas_check-circle</v-icon>
                    </span>
            </div>

            <div class="row">
                    <div class="col_1">
                        <p> 3 </p>
                    </div>
                    <div style="margin-left: 4px">
                        Start Burp proxy and browse any website. You will see traffic in 
                        <span v-if="!burpCollectionURL"> Burp collection </span>
                        <a :href="burpCollectionURL" v-else> <span style="text-decoratoin: underline; color: var(--themeColor)">Burp collection</span> </a>
                    </div>
                    <span v-if="this.sendDataLoading === 1" style="padding-left: 4px">
                        <v-progress-circular indeterminate color="grey" :size="12" :width="1.5"></v-progress-circular>
                    </span>
                    <span v-if="this.sendDataLoading === 2">
                        <v-icon color="green">$fas_check-circle</v-icon>
                    </span>
            </div>
        </div>
    </div>
</template>

<script>
import api from '../api.js'
import func from '@/util/func'

import BurpSuiteIntegration from './BurpSuiteIntegration.vue'
export default {
    name: "BurpsuiteSource",
    components: {
        BurpSuiteIntegration
    },
    data() {
        return {
            downloadLoading: 0,
            sendInitialDataLoading: 0,
            sendDataLoading: 0,
            burpCollectionURL: null
        }
    },
    methods: {
        async downloadBurpJar() {
            this.downloadLoading = 1
            this.sendInitialDataLoading = 0
            this.sendDataLoading = 0

            let downloadTime = func.timeNow()
            console.log(downloadTime);

            let response = await api.downloadBurpPluginJar()
            const href = URL.createObjectURL(response);
            // create "a" HTML element with href to file & click
            const link = document.createElement('a');
            link.href = href;
            link.setAttribute('download', 'Akto.jar'); //or any other extension
            document.body.appendChild(link);
            link.click();
            // clean up "a" element & remove ObjectURL
            document.body.removeChild(link);
            URL.revokeObjectURL(href);

            this.downloadLoading = 2
            this.sendInitialDataLoading= 1


            let interval = setInterval(() => {
                api.fetchBurpPluginInfo().then((response) => {
                let lastBootupTimestamp = response.burpPluginInfo.lastBootupTimestamp
                if (lastBootupTimestamp > downloadTime) {
                    this.sendInitialDataLoading= 2
                    this.sendDataLoading = 1
                    if (response.burpPluginInfo.lastDataSentTimestamp > downloadTime) {
                        clearInterval(interval)
                        this.sendDataLoading = 2
                        this.burpCollectionURL = "/dashboard/observe/inventory"
                    }
                }
            })
            }, 5000)

        }
    }
}
</script>

<style lang="sass" scoped>

.di-flex-bottom 
    display: flex
    gap: 8px
    padding-top: 20px
    padding-bottom: 11px

.col_1 
    box-sizing: border-box
    height: 20px
    left: 0px
    top: 0px
    border: 2px solid var(--themeColor)
    border-radius: 50%
    text-align: center
    font-style: normal
    font-weight: 600
    font-size: 14px
    line-height: 16px
    color: var(--themeColor)

.clickable-docs
    cursor: pointer
    color: var(--quickStartTheme) !important
    text-decoration: underline

.container
    display: flex
    flex-direction: column
    justify-content: space-between

.row
    display: flex   
    margin-bottom: 4px
    
.row > div:first-child 
    width: 20px
</style>