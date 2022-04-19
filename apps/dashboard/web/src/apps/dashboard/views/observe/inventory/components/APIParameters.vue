<template>
    <div v-if="loading">
        <spinner/>
    </div>
    <div v-else>
        <v-row>
            <v-col md="6">
                <sensitive-params-card title="Sensitive parameters" :sensitiveParams="sensitiveParamsForChart"/>
            </v-col>
            
            <v-col md="6">
                <a-card title="Traffic" icon="$fas_chart-line" style="height: 330px">
                    <spinner v-if="loadingTrafficData"/>
                    <line-chart
                        type='spline'
                        color='#6200EA'
                        :areaFillHex="true"
                        :height="230"
                        title="Traffic"
                        :data="trafficTrend"
                        :defaultChartOptions="{legend:{enabled: false}}"
                        background-color="rgba(0,0,0,0.0)"
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
                />
            </template>
            <template slot="Response">
                <simple-table 
                    :headers="headers" 
                    :items="responseItems"  
                    :actions="actions"
                    name="Response" 
                    sortKeyDefault="sensitive" 
                    :sortDescDefault="true"
                />
            </template>
            <template slot="Values">
                <sample-data :messages="sampleData" v-if="sampleData"/>
                <spinner v-else/>
            </template>
            <template slot="Sensitive Values">
                <sample-data :messages="sensitiveSampleData" v-if="sensitiveSampleData"/>
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
import SampleData from './SampleData.vue'
import Spinner from '@/apps/dashboard/shared/components/Spinner'

import api from '../api'

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
    },
    props: {
        urlAndMethod: obj.strR,
        apiCollectionId: obj.numR
    },
    data () {
        return {
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
                    value: 'date',
                    sortKey: 'detectedTs'
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
                location: (x.responseCode == -1 ? 'Request' : 'Response') + ' ' + (x.isHeader ? 'headers' : 'payload'),
                x: x
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
        ...mapState('inventory', ['parameters', 'loading']),
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
            return ret
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
                    color: ["#6200EAFF", "#6200EADF", "#6200EABF", "#6200EA9F", "#6200EA7F", "#6200EA5F", "#6200EA3F", "#6200EA1F"][i]
                }
            })

            ret.push({
                name: "Generic",
                y: numGenericParams,
                color: "#7D787838"
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
        let resp = await api.fetchEndpointTrafficData(this.url, this.apiCollectionId, this.method, now - 600 * 24 * 60 * 60, now)
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
            this.sensitiveSampleData.push({"message": c, "highlightPaths": sensitiveDataResp.sensitiveSampleData[c]})
        }
    }
}
</script>

<style lang="sass" scoped>
.table-title
    font-size: 16px    
    color: #47466A
    font-weight: 500
    padding-top: 16px
</style>