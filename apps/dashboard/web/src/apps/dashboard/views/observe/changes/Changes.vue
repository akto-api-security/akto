<template>
    <div>
        <simple-layout title="API Changes"/>
        <div class="d-flex pa-4">
            <count-box title="New endpoints" :count="newEndpoints.length" colorTitle="Total" />
            <count-box title="New sensitive endpoints" :count="newSensitiveEndpoints.length" colorTitle="Overdue" />
            <count-box title="New parameters" :count="newParameters.length" colorTitle="Total" />
            <count-box title="New sensitive parameters" :count="newSensitiveParameters.length" colorTitle="Overdue" />
        </div>
        <a-card title="Changes" icon="$fas_chart-line" class="ma-5">
            <line-chart
                type='spline'
                color='#6200EA'
                :areaFillHex="true"
                :height="250"
                title="New Endpoints"
                :data="newEndpointsTrend"
                :defaultChartOptions="{legend:{enabled: false}}"
                background-color="rgba(0,0,0,0.0)"
                :text="true"
                :input-metrics="[{data: newParamsTrend, name: 'New Parameters'}]"
                class="pa-5"
            />
        </a-card>
        <layout-with-tabs title="" :tabs="['New endpoints', 'New parameters']">
            <template slot="actions-tray">
                <div class="d-flex jc-end">
                    <v-btn icon color="#6200EA" @click="refreshPage"><v-icon>$fas_sync</v-icon></v-btn>
                </div>
            </template>
            <template slot="New endpoints">
                <simple-table 
                    :headers="endpointHeaders" 
                    :items="newEndpoints" 
                    name="New endpoints" 
                    sortKeyDefault="added" 
                    :sortDescDefault="true" 
                    @rowClicked="goToEndpoint"
                >
                    <template #item.sensitive="{item}">
                        <sensitive-chip-group :sensitiveTags="Array.from(item.sensitiveTags || new Set())" />
                    </template>
                </simple-table>
            </template>
            <template slot="New parameters">
                <simple-table 
                    :headers="parameterHeaders" 
                    :items="newParameters" 
                    name="New parameters" 
                    sortKeyDefault="added" 
                    :sortDescDefault="true"
                    @rowClicked="goToEndpoint"
                >
                    <template #item.type="{item}">
                        <sensitive-chip-group :sensitiveTags="[item.type]" />
                    </template>
                </simple-table>
            </template>
        </layout-with-tabs>
    </div>    
</template>

<script>
import SimpleLayout from '@/apps/dashboard/layouts/SimpleLayout'
import LayoutWithTabs from '@/apps/dashboard/layouts/LayoutWithTabs'
import ACard from '@/apps/dashboard/shared/components/ACard'
import CountBox from '@/apps/dashboard/shared/components/CountBox'
import LineChart from '@/apps/dashboard/shared/components/LineChart'
import SimpleTable from '@/apps/dashboard/shared/components/SimpleTable'
import SensitiveChipGroup from '@/apps/dashboard/shared/components/SensitiveChipGroup'
import func from '@/util/func'
import constants from '@/util/constants'
import {mapState} from 'vuex'

export default {
    name: "ApiChanges",
    components: { 
        SimpleLayout, 
        CountBox, 
        ACard, 
        LineChart, 
        LayoutWithTabs,
        SimpleTable,
        SensitiveChipGroup,
        LineChart
    },
    data () {
        return {
            endpointHeaders: [
                {
                    text: '',
                    value: 'color'
                },
                {
                    text: 'Endpoint',
                    value: 'endpoint'
                },
                {
                    text: 'Collection',
                    value: 'apiCollectionName'
                },
                {
                    text: 'Method',
                    value: 'method'
                },
                {
                    text: 'Sensitive Params',
                    value: 'sensitive'
                },
                {
                    text: constants.DISCOVERED,
                    value: 'added',
                    sortKey: 'detectedTs'
                }
            ],
            parameterHeaders: [
                {
                    text: '',
                    value: 'color'
                },
                {
                    text: 'Name',
                    value: 'name'
                },
                {
                    text: 'Type',
                    value: 'type'
                },
                {
                    text: 'Endpoint',
                    value: 'endpoint'
                },
                {
                    text: 'Collection',
                    value: 'apiCollectionName'
                },
                {
                    text: 'Method',
                    value: 'method'
                },
                {
                    text: 'Location',
                    value: 'location'
                },
                {
                    text: constants.DISCOVERED,
                    value: 'added',
                    sortKey: 'detectedTs'
                }
            ]
        }
    },
    methods: {
        prepareItemForTable(x) {
            let idToNameMap = this.mapCollectionIdToName
            return {
                color: func.isSubTypeSensitive(x) ? this.$vuetify.theme.themes.dark.redMetric : this.$vuetify.theme.themes.dark.greenMetric,
                name: x.param.replaceAll("#", ".").replaceAll(".$", ""),
                endpoint: x.url,
                method: x.method,
                added: func.prettifyEpoch(x.timestamp),
                location: (x.responseCode == -1 ? 'Request' : 'Response') + ' ' + (x.isHeader ? 'headers' : 'payload'),
                type: x.subType,
                detectedTs: x.timestamp,
                apiCollectionId: x.apiCollectionId,
                apiCollectionName: idToNameMap[x.apiCollectionId] || '-'
            }
        },
        goToEndpoint (row) {
            let routeObj = {
                name: 'apiCollection/urlAndMethod',
                params: {
                    apiCollectionId: row.apiCollectionId,
                    urlAndMethod: btoa(row.endpoint+ " " + row.method)
                }
            }

            this.$router.push(routeObj)
        },
        refreshPage() {
            this.$store.dispatch('changes/loadRecentParameters')
        },
        changesTrend (data) {
            let todayDate = func.todayDate()
            let twoMonthsAgo = func.incrDays(todayDate, -61)
            
            let currDate = twoMonthsAgo
            let ret = []
            let dateToCount = data.reduce((m, e) => { 
                let detectDate = func.toYMD(new Date(e.detectedTs*1000))
                m[detectDate] = (m[detectDate] || 0 ) + 1
                return m
            }, {})
            while (currDate <= todayDate) {
                ret.push([func.toDate(func.toYMD(currDate)), dateToCount[func.toYMD(currDate)] || 0])
                currDate = func.incrDays(currDate, 1)
            }
            return ret
        }
    },
    computed: {
        ...mapState('changes', ['apiCollection']),
        mapCollectionIdToName() {
            return this.$store.state.collections.apiCollections.reduce((m, e) => {
                m[e.id] = e.name
                return m
            }, {})
        },
        newEndpoints() {
            let now = func.timeNow()
            return func.groupByEndpoint(this.apiCollection, this.mapCollectionIdToName).filter(x => x.detectedTs > now - func.recencyPeriod)
        },
        newEndpointsTrend() {
            return this.changesTrend(this.newEndpoints)
        },
        newParameters() {
            let now = func.timeNow()
            return this.apiCollection.filter(x => x.timestamp > now - func.recencyPeriod).map(this.prepareItemForTable)
        },
        newParamsTrend() {
            return this.changesTrend(this.newParameters)
        },
        newSensitiveEndpoints() {
            let now = func.timeNow()
            return func.groupByEndpoint(this.apiCollection, this.mapCollectionIdToName).filter(x => x.detectedTs > now - func.recencyPeriod && x.sensitive > 0)
        },
        newSensitiveParameters() {
            let now = func.timeNow()
            return this.apiCollection.filter(x => x.timestamp > now - func.recencyPeriod && func.isSubTypeSensitive(x)).map(this.prepareItemForTable)
        },
    },
    mounted() {
        this.refreshPage()
    }    

}
</script>

<style lang="sass" scoped>

</style>