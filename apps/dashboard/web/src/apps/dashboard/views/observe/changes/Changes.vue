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
                    <v-tooltip bottom>
                        <template v-slot:activator='{on, attrs}'>
                            <v-btn 
                                icon 
                                color="#47466A" 
                                @click="refreshPage(true)"
                                v-on="on"
                                v-bind="attrs"
                            >
                                    <v-icon>$fas_redo</v-icon>
                            </v-btn>
                        </template>
                        Refresh
                    </v-tooltip>
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
                    
                    <template #add-new-row-btn="{filteredItems}">
                        <div class="ma-1 d-flex">
                            <v-dialog
                                :model="showDialog1"
                                width="600px"
                            >
                            <template v-slot:activator="{ on, attrs }">
                                <v-btn
                                    color="#47466A"
                                    icon
                                    dark
                                    v-bind="attrs"
                                    v-on="on"
                                    @click="showDialog1 = !showDialog1"
                                >
                                <v-tooltip bottom>
                                    <template v-slot:activator='{ on, attrs }'>
                                        <v-icon color="#47466A" size="16" v-bind="attrs" v-on="on" >$fas_lock</v-icon>
                                    </template>
                                    Mark sensitive
                                </v-tooltip>
                                </v-btn>
                            </template>
                                <batch-operation 
                                    title="Parameters" 
                                    :items="filteredItems.filter(x => !isSubTypeSensitive(x.x)).map(toFilterListObj)" 
                                    operation-name="Mark sensitive"
                                    @btnClicked="markAllSensitive(true, $event)"
                                />
                            </v-dialog>

                            <v-dialog
                                :model="showDialog2"
                                width="600px"
                            >
                            <template v-slot:activator="{ on, attrs }">
                                <v-btn
                                    color="#47466A"
                                    icon
                                    dark
                                    v-bind="attrs"
                                    v-on="on"
                                    @click="showDialog2 = !showDialog2"
                                >
                                <v-tooltip bottom>
                                    <template v-slot:activator='{ on, attrs }'>
                                        <v-icon color="#47466A" size="16" v-bind="attrs" v-on="on" >$fas_lock-open</v-icon>
                                    </template>
                                    Unmark sensitive
                                </v-tooltip>
                                </v-btn>
                            </template>
                                <batch-operation 
                                    title="Parameters" 
                                    :items="newParameters.filter(x => isSubTypeSensitive(x.x)).map(toFilterListObj)" 
                                    operation-name="Unmark sensitive"
                                    @btnClicked="markAllSensitive(false, $event)"
                                />
                            </v-dialog>
                        </div>
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
import BatchOperation from './components/BatchOperation'
import api from './api.js'

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
        LineChart,
        BatchOperation
    },
    data () {
        return {
            showDialog1: false,
            showDialog2: false,
            endpointHeaders: [
                {
                    text: '',
                    value: 'color'
                },
                {
                    text: 'Endpoint',
                    value: 'parameterisedEndpoint'
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
                  text: 'Access Type',
                  value: 'access_type',
                  sortKey: 'access_type'
                },
                {
                  text: 'Auth Type',
                  value: 'auth_type',
                  sortKey: 'auth_type'
                },
                {
                  text: 'Last Seen',
                  value: 'last_seen',
                  sortKey: 'last_seen'
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
        isSubTypeSensitive(x) {
            return func.isSubTypeSensitive(x)
        },
        markAllSensitive (sensitive, {items}) {
            let valueSet = new Set([...items.map(x => x.value)])
            api.bulkMarkSensitive(sensitive, this.newParameters.filter(n => valueSet.has(this.toFilterListObj(n).value))).then(resp => {
                window._AKTO.$emit('SHOW_SNACKBAR', {
                    show: true,
                    text: `${items.length}` + ` items ${sensitive ? '':'un'}marked sensitive`,
                    color: 'green'
                })
                this.refreshPage(true)
            }).catch(() => {
                window._AKTO.$emit('SHOW_SNACKBAR', {
                    show: true,
                    text: `Error in ${sensitive ? '':'un'}marking sensitive!`,
                    color: 'red'
                })
            })
            this.showDialog1 = false
            this.showDialog2 = false
        },
        toFilterListObj(x) {
            return {
                value: x.name + " " + x.location + " " + x.method + " " + x.endpoint + " " + x.apiCollectionName,
                title: x.name,
                subtitle: x.location + " " + x.method + " " + x.endpoint + " (" + x.apiCollectionName + ")"
            }
        },
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
                apiCollectionName: idToNameMap[x.apiCollectionId] || '-',
                x: x
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
        refreshPage(hardRefresh) {
            if (hardRefresh || ((new Date() / 1000) - this.lastFetched > 60*5)) {
                this.$store.dispatch('changes/loadRecentParameters')
                this.$store.dispatch('changes/fetchApiInfoListForRecentEndpoints')
            }
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
        ...mapState('changes', ['apiCollection', 'apiInfoList', 'lastFetched']),
        mapCollectionIdToName() {
            return this.$store.state.collections.apiCollections.reduce((m, e) => {
                m[e.id] = e.name
                return m
            }, {})
        },
        newEndpoints() {
            let now = func.timeNow()
            return func.groupByEndpoint(this.apiCollection,this.apiInfoList, this.mapCollectionIdToName).filter(x => x.detectedTs > now - func.recencyPeriod)
        },
        newEndpointsTrend() {
            return this.changesTrend(this.newEndpoints)
        },
        newParameters() {
            let now = func.timeNow()
            let listParams = this.apiCollection.filter(x => x.timestamp > now - func.recencyPeriod).map(this.prepareItemForTable)
            return listParams.sort((a, b) => {
                if (a.detectedTs > b.detectedTs + 3600) {
                    return -1
                } else if (a.detectedTs < b.detectedTs - 3600) {
                    return 1
                } else {
                    return func.isSubTypeSensitive(a.x) > func.isSubTypeSensitive(b.x) ? -1 : 1
                }
            })
        },
        newParamsTrend() {
            return this.changesTrend(this.newParameters)
        },
        newSensitiveEndpoints() {
            let now = func.timeNow()
            return func.groupByEndpoint(this.apiCollection,this.apiInfoList, this.mapCollectionIdToName).filter(x => x.detectedTs > now - func.recencyPeriod && x.sensitive > 0)
        },
        newSensitiveParameters() {
            let now = func.timeNow()
            return this.apiCollection.filter(x => x.timestamp > now - func.recencyPeriod && func.isSubTypeSensitive(x)).map(this.prepareItemForTable)
        },
    },
    mounted() {
        this.refreshPage(false)
    }    

}
</script>

<style lang="sass" scoped>

</style>