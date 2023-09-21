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
                    <div class="info-box">
                        <span style="text-decoration: underline; cursor: pointer" @click="downloadBurpJar">Download</span>
                        Akto's Burp extension
                    </div>
            </div>

            <div class="row">
                    <div class="col_1">
                        <p> 2 </p>
                    </div>
                    <div class="info-box">
                        Open Burp and add the downloaded jar file in extension tab
                    </div>
            </div>

            <div class="row">
                    <div class="col_1">
                        <p> 3 </p>
                    </div>
                    <div class="info-box">
                        Once the plugin is loaded click on "options" tab inside the plugin
                    </div>
            </div>

            <div class="row">
                    <div class="col_1">
                        <p> 4 </p>
                    </div>
                    <div class="info-box">
                        Copy AKTO_IP: 
                        <span style="color: var(--themeColor)">{{ aktoIp }}</span>
                        and AKTO_TOKEN:
                        <span style="color: var(--themeColor)">{{ aktoToken }}</span>
                        and paste in the options tab
                    </div>
            </div>

            <div class="row">
                    <div class="col_1">
                        <p> 5 </p>
                    </div>
                    <div class="info-box">
                        Start Burp proxy and browse any website. You will see traffic in 
                        <span v-if="!burpCollectionURL"> Burp collection </span>
                        <a :href="burpCollectionURL" v-else> <span style="text-decoratoin: underline; color: var(--themeColor)">Burp collection</span> </a>
                    </div>
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
            burpCollectionURL: null,
            burpGithubLink: null,
            aktoIp: null,
            aktoToken: null,
        }
    },

    mounted() {
        api.fetchBurpPluginDownloadLink().then((resp) => {
            if (resp && resp.burpGithubLink) {
                this.burpGithubLink = resp.burpGithubLink
            }
        })

        api.fetchBurpCredentials().then((resp) => {
            if (!resp) return
            this.aktoIp = resp.host
            this.aktoToken = resp.apiToken.key
        })

    },

    methods: {
        async downloadBurpJar() {
            let downloadTime = func.timeNow()
            let showBurpPluginConnectedFlag = false

            api.downloadBurpPluginJar()
            window.open(this.burpGithubLink)

            let interval = setInterval(() => {
                api.fetchBurpPluginInfo().then((response) => {
                    let lastBootupTimestamp = response.burpPluginInfo.lastBootupTimestamp
                    if (lastBootupTimestamp > downloadTime) {
                        if (showBurpPluginConnectedFlag) {
                            window._AKTO.$emit('SHOW_SNACKBAR', {
                                text: "Burp plugin connected",
                                color: 'success'
                            })
                        }
                        showBurpPluginConnectedFlag = false
                        if (response.burpPluginInfo.lastDataSentTimestamp > downloadTime) {
                            clearInterval(interval)
                            this.burpCollectionURL = "/dashboard/observe/inventory"
                            window._AKTO.$emit('SHOW_SNACKBAR', {
                                text: "Data received from burp plugin",
                                color: 'success'
                            })
                        }
                    }
                })
            }, 1000)

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

.info-box
    margin-left: 4px
    max-width: 800px
</style>