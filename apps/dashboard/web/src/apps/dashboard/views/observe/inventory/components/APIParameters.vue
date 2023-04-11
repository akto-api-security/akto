<template>
    <div v-if="parametersLoading">
        <spinner/>
    </div>
    <div v-else>
        <div class="fix-at-top" v-if="allSamples && allSamples.length > 0" style="display: none">
            <v-btn dark depressed color="var(--gptColor)" @click="showGPTScreen()">
                Ask AktoGPT 
                <v-icon size="16">$chatGPT</v-icon>
            </v-btn>
        </div>

        <v-dialog
            v-model="showGptDialog"
            width="fit-content" 
            content-class="dialog-no-shadow"
            overlay-opacity="0.7"
        >
            <div class="gpt-dialog-container ma-0">
                <chat-gpt-input
                    v-if="showGptDialog"
                    :items="chatGptPrompts"
                />
            </div>

        </v-dialog>

        <v-row>
            <v-col md="6">
                <sensitive-params-card title="Sensitive parameters" :sensitiveParams="sensitiveParamsForChart"/>
            </v-col>
            
            <v-col md="6">
                <a-card title="Traffic" icon="$fas_chart-line" style="height: 330px">
                    <spinner v-if="loadingTrafficData"/>
                    <line-chart
                        type='spline'
                        color='var(--themeColor)'
                        :areaFillHex="true"
                        :height="230"
                        title="Traffic"
                        :data="trafficTrend"
                        :defaultChartOptions="{legend:{enabled: false}}"
                        background-color="var(--transparent)"
                        :text="true"
                        :input-metrics="[]"
                        class="pa-5"
                        v-else
                    />
                </a-card>
            </v-col>
        </v-row>
        <layout-with-tabs :tabs="['Request', 'Response', 'Values', 'Sensitive Values']">
            <template slot="Request">
                <simple-table 
                    :headers="headers" 
                    :items="requestItems" 
                    :actions="actions"
                    name="Request" 
                    sortKeyDefault="sensitive" 
                    :sortDescDefault="true" 
                >
                </simple-table>
            </template>
            <template slot="Response">
                <simple-table 
                    :headers="headers" 
                    :items="responseItems"  
                    :actions="actions"
                    name="Response" 
                    sortKeyDefault="sensitive" 
                    :sortDescDefault="true"
                >
                </simple-table>
            </template>
            <template slot="Values">
                <sample-data-list :messages="sampleData" v-if="sampleData"/>
                <spinner v-else/>
            </template>
            <template slot="Sensitive Values">
                <sample-data-list :messages="sensitiveSampleData" v-if="sensitiveSampleData"/>
                <spinner v-else/>
            </template>
        </layout-with-tabs>
    </div>    
</template>

<script>
import SimpleTable from '@/apps/dashboard/shared/components/SimpleTable'
import ACard from '@/apps/dashboard/shared/components/ACard'
import LayoutWithTabs from '@/apps/dashboard/layouts/LayoutWithTabs'
import DonutChart from '@/apps/dashboard/shared/components/DonutChart'
import {mapState} from 'vuex'
import obj from '@/util/obj'
import func from '@/util/func'
import SensitiveParamsCard from '@/apps/dashboard/shared/components/SensitiveParamsCard'
import LineChart from '@/apps/dashboard/shared/components/LineChart'
import SampleData from '@/apps/dashboard/shared/components/SampleData'
import Spinner from '@/apps/dashboard/shared/components/Spinner'
import ChatGptInput from '@/apps/dashboard/shared/components/inputs/ChatGptInput.vue'

import api from '../api'
import SampleDataList from '@/apps/dashboard/shared/components/SampleDataList'

export default {
    name: "ApiParameters",
    components: {
    SimpleTable,
    ACard,
    LayoutWithTabs,
    DonutChart,
    SensitiveParamsCard,
    LineChart,
    Spinner,
    SampleData,
    SampleDataList,
    ChatGptInput
},
    props: {
        urlAndMethod: obj.strR,
        apiCollectionId: obj.numR
    },
    data () {
        return {
            showGptDialog:false,
            chatGptPrompts: [
                {
                    icon: "$fas_user-lock",
                    label: "Fetch Sensitive Params",
                    prepareQuery: () => { return {
                        type: "list_sensitive_params",
                        meta: {
                            "sampleData": this.parseMsg(this.allSamples[0].message)
                        }                        
                    }},
                    callback: (data) => console.log("callback create api groups", data)
                }
            ],
            headers: [
                {
                    text: '',
                    value: 'color'
                },
                {
                    text: 'Name',
                    value: 'name'
                },
                {
                    text: 'Sensitive',
                    value: 'sensitive'
                },
                {
                    text: 'Parameter type',
                    value: 'type'
                },
                {
                    text: 'Location',
                    value: 'location'  
                },
                {
                    text: 'Discovered',
                    value: 'date'
                },
                {
                  text: 'Values',
                  value: 'domain',
                }
            ],
          actions: [
                {
                    isValid: item => this.isValid(item),
                    icon: item => item.x.savedAsSensitive ? '$fas_lock-open' : '$fas_lock',
                    text: item => item.x.savedAsSensitive ? 'Unmark sensitive' : 'Mark sensitive',
                    func: item => this.toggleSensitiveFieldFunc(item),
                    success: (resp, item) => this.toggleSuccessFunc(resp, item),
                    failure: (err, item) => this.toggleFailureFunc(err, item)
                }
            ],
            loadingTrafficData: false,
            trafficInfo: {},
            sampleData: null,
            sensitiveSampleData: null
        }  
    },
    methods: {
        parseMsg(jsonStr) {
            let json = JSON.parse(jsonStr)
            return {
                request: JSON.parse(json.requestPayload),
                response: JSON.parse(json.responsePayload)
            }
        },
        showGPTScreen(){
            this.showGptDialog=true
            console.log(this.sampleData)
            console.log(this.sensitiveSampleData)
        },
        prettifyDate(ts) {
            if (ts) {
                return func.prettifyEpoch(ts)
            } else {
                return '-'
            }
        },
        prepareItem(x) {
            return {
                color: x.savedAsSensitive || func.isSubTypeSensitive(x) ? this.$vuetify.theme.themes.dark.redMetric: this.$vuetify.theme.themes.dark.greenMetric,
                name: x.param.replaceAll("#", ".").replaceAll(".$", ""),
                sensitive: func.isSubTypeSensitive(x) ? 'Yes' : '',
                type: x.subType.name,
                container: x.isHeader ? 'Headers' : 'Payload ',
                date: this.prettifyDate(x.timestamp),
                detectedTs: x.timestamp,
                location: (x.responseCode === -1 ? 'Request' : 'Response') + ' ' + (x.isHeader ? 'headers' : 'payload'),
                x: x,
                domain: func.prepareDomain(x),
                valuesString: func.prepareValuesTooltip(x)
            }
        },
        toggleSensitiveFieldFunc (item) {
            item.x.sensitive = !item.x.savedAsSensitive
            return this.$store.dispatch('inventory/toggleSensitiveParam', item.x)
        },
        toggleSuccessFunc (resp, item) {
            item.color
            window._AKTO.$emit('SHOW_SNACKBAR', {
                show: true,
                text: `${item.name} `+ (item.x.sensitive ? '' : 'un') +`marked as sensitive successfully!`,
                color: 'green'
            })
        },
        toggleFailureFunc (err, item) {
            window._AKTO.$emit('SHOW_SNACKBAR', {
                show: true,
                text: `An error occurred while `+ (item.x.sensitive ? '' : 'un')+`marking ${item.name} as sensitive!`,
                color: 'red'
            })
        },
        isValid (item) {
            let obj = {...item.x}
            obj.savedAsSensitive = false
            obj.sensitive = false
            return !func.isSubTypeSensitive(obj)
        }
    },
    computed: {
        ...mapState('inventory', ['parameters', 'parametersLoading']),
        allSamples(){
            return  [...(this.sampleData || []), ...(this.sensitiveSampleData || [])]
        },
        url () {
            return this.urlAndMethod.split(" ")[0]
        },
        method () {
            return this.urlAndMethod.split(" ")[1]
        },
        sensitiveParams() {
            return this.parameters.filter(x => x.subType === "CUSTOM" || func.isSubTypeSensitive(x))
        },
        trafficTrend () {
            let dateToCount = this.trafficInfo
            let todayDate = func.todayDate()

            if (!dateToCount || Object.keys(dateToCount).length == 0) {
                return []
            }

            let currDate = new Date(func.toDate(Math.min(...Object.keys(dateToCount))))
            let ret = []

            while (currDate <= todayDate) {
                ret.push([func.toDate(func.toYMD(currDate)), dateToCount[func.toYMD(currDate)] || 0])
                currDate = func.incrDays(currDate, 1)
            }
            return [{"data": ret, "color": "#6200EA", "name": "Traffic"}]
        },
        sensitiveParamsForChart() {
            if (this.parameters.length == 0) {
                return []
            }

            let numGenericParams = this.parameters.length - this.sensitiveParams.length
            let ret = Object.entries(this.sensitiveParams.reduce((z, e) => {
                let key = func.isSubTypeSensitive(e) ? e.subType.name : 'Generic'
                z[key] = (z[key] || 0) + 1
                return z
            }, {})).map((x, i) => {
                return {
                    name: x[0],
                    y: x[1],
                    color: ["var(--themeColor)", "var(--themeColor2)", "var(--themeColor3)", "var(--themeColor4)", "var(--themeColor6)", "var(--themeColor7)", "var(--themeColor8)", "var(--themeColor11)"][i]
                }
            })

            ret.push({
                name: "Generic",
                y: numGenericParams,
                color: "var(--hexColor16)"
            })
            
            return ret
        },
        requestItems() {
            return this.parameters.filter(x => x.responseCode == -1).map(this.prepareItem)
        },
        responseItems() {
            return this.parameters.filter(x => x.responseCode > -1).map(this.prepareItem)
        }
    },
    async mounted() {
        this.$emit('mountedView', {apiCollectionId: this.apiCollectionId, urlAndMethod: this.urlAndMethod, type: 2})
        if (
            this.$store.state.inventory.apiCollectionId !== this.apiCollectionId || 
            this.$store.state.inventory.url !== this.url ||
            this.$store.state.inventory.method !== this.method
        ) {
            let urlIdentifier = {apiCollectionId: this.apiCollectionId, url: this.url, method: this.method}
            await this.$store.dispatch('inventory/loadParamsOfEndpoint', urlIdentifier)
        }

        let now = func.timeNow()
        this.loadingTrafficData = true
        let resp = await api.fetchEndpointTrafficData(this.url, this.apiCollectionId, this.method, now - 60 * 24 * 60 * 60, now)
        this.loadingTrafficData = false
        this.trafficInfo = resp.traffic

        let sampleDataResp = await api.fetchSampleData(this.url, this.apiCollectionId, this.method)
        let data = sampleDataResp.sampleDataList.length > 0 ? sampleDataResp.sampleDataList[0].samples : []
        this.sampleData = []
        data.forEach((x) => {
            this.sampleData.push({"message": x, "highlightPaths": []})
        })

        let sensitiveDataResp = await api.fetchSensitiveSampleData(this.url, this.apiCollectionId, this.method)
        this.sensitiveSampleData = []
        for (const c in sensitiveDataResp.sensitiveSampleData) {
            let paramInfoList = sensitiveDataResp.sensitiveSampleData[c]
            if (!paramInfoList) {
                paramInfoList = []
            }

            let highlightPaths = paramInfoList.map((x) => {
                let subType = x["subType"]
                let val = {}
                if (subType) {
                    val["value"] = subType["name"]
                    val["asterisc"] = false
                    val["highlight"] = true
                    x["highlightValue"] = val
                    return x
                }
            })

            this.sensitiveSampleData.push({"message": c, "highlightPaths": highlightPaths})
        }
    }
}
</script>

<style lang="sass" scoped>
.fix-at-top
    position: absolute
    right: 260px
    top: 18px
.gpt-dialog-container
    min-height: 300px
    background-color: var(--gptBackground)
.table-title
    font-size: 16px    
    color: var(--themeColorDark)
    font-weight: 500
    padding-top: 16px
.v-tooltip__content
    font-size: 15px !important
</style>
