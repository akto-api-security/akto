<template>
    <div v-if="loading">
        <spinner/>
    </div>
    <div v-else>
        <simple-layout title="API Changes"/>
        <div class="d-flex jc-end">
            <date-range v-model="dateRange"/>
        </div>
        <div class="d-flex pa-4">
            <count-box title="New endpoints" :count="newEndpoints.length" colorTitle="Total" />
            <count-box title="New sensitive endpoints" :count="newSensitiveEndpoints.length" colorTitle="Overdue" />
            <count-box title="New parameters" :count="newParametersCount" colorTitle="Total" />
            <count-box title="New sensitive parameters" :count="newSensitiveParameters.length" colorTitle="Overdue" />
        </div>
        <a-card title="Changes" icon="$fas_chart-line" class="ma-5">
            <line-chart
                type='spline'
                color='var(--themeColor)'
                :areaFillHex="true"
                :height="250"
                title="New Endpoints"
                :data="newEndpointsTrend"
                :defaultChartOptions="{legend:{enabled: false}}"
                background-color="var(--transparent)"
                :text="true"
                :input-metrics="[{data: newParamsTrend, name: 'New Parameters'}]"
                class="pa-5"
            />
        </a-card>
        <layout-with-tabs title="" :tabs="tabList">
            <template slot="actions-tray">
                <div class="d-flex jc-end">
                    <v-tooltip bottom>
                        <template v-slot:activator='{on, attrs}'>
                            <v-btn 
                                icon 
                                color="var(--themeColorDark)" 
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
                    <template #item.sensitiveTags="{item}">
                        <sensitive-chip-group :sensitiveTags="Array.from(item.sensitiveTags || new Set())" />
                    </template>
                    <template #item.tags="{item}">
                        <tag-chip-group :tags="Array.from(item.tags || [])" />
                    </template>
                </simple-table>
            </template>
            <template slot="New parameters">
                <server-table 
                    :headers="parameterHeaders" 
                    name="New parameters" 
                    sortKeyDefault="timestamp" 
                    :sortDescDefault="true"
                    @rowClicked="goToEndpoint"
                    :fetchParams="fetchRecentParams"
                    :processParams="prepareItemForTable"
                    :getColumnValueList="getColumnValueList"
                    :hideDownloadCSVIcon="true"
                >
                    <template #item.type="{item}">
                        <sensitive-chip-group :sensitiveTags="[item.type]" />
                    </template>
                    <!-- <template #add-new-row-btn="{filters, filterOperators, sortKey, sortDesc, total}">
                        <div class="ma-1 d-flex">
                            <v-dialog
                                :model="showDialog1"
                                width="600px"
                            >
                            <template v-slot:activator="{ on, attrs }">
                                <v-btn
                                    color="var(--themeColorDark)"
                                    icon
                                    dark
                                    v-bind="attrs"
                                    v-on="on"
                                    @click="showDialog1 = !showDialog1"
                                >
                                <v-tooltip bottom>
                                    <template v-slot:activator='{ on, attrs }'>
                                        <v-icon color="var(--themeColorDark)" size="16" v-bind="attrs" v-on="on" >$fas_lock</v-icon>
                                    </template>
                                    Mark sensitive
                                </v-tooltip>
                                </v-btn>
                            </template>
                                <batch-operation 
                                    title="Parameters" 
                                    :itemsSearch="{filters, filterOperators, sortKey, sortDesc, total, isSensitive: true}" 
                                    operation-name="Mark sensitive"
                                    :fetchParams="fetchRecentParams"
                                    @btnClicked="markAllSensitive(true, $event)"
                                />
                            </v-dialog>

                            <v-dialog
                                :model="showDialog2"
                                width="600px"
                            >
                            <template v-slot:activator="{ on, attrs }">
                                <v-btn
                                    color="var(--themeColorDark)"
                                    icon
                                    dark
                                    v-bind="attrs"
                                    v-on="on"
                                    @click="showDialog2 = !showDialog2"
                                >
                                <v-tooltip bottom>
                                    <template v-slot:activator='{ on, attrs }'>
                                        <v-icon color="var(--themeColorDark)" size="16" v-bind="attrs" v-on="on" >$fas_lock-open</v-icon>
                                    </template>
                                    Unmark sensitive
                                </v-tooltip>
                                </v-btn>
                            </template>
                                <batch-operation 
                                    title="Parameters" 
                                    :itemsSearch="{filters, filterOperators, sortKey, sortDesc, total, isSensitive: false}" 
                                    operation-name="Unmark sensitive"
                                    :fetchParams="fetchSensitiveParams"
                                    @btnClicked="markAllSensitive(false, $event)"
                                />
                            </v-dialog>
                        </div>
                    </template> -->
                </server-table>
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
import ServerTable from '@/apps/dashboard/shared/components/ServerTable'
import SensitiveChipGroup from '@/apps/dashboard/shared/components/SensitiveChipGroup'
import TagChipGroup from '@/apps/dashboard/shared/components/TagChipGroup'
import obj from '@/util/obj'
import func from '@/util/func'
import constants from '@/util/constants'
import {mapState} from 'vuex'
import BatchOperation from './components/BatchOperation'
import api from './api.js'
import inventorApi from '../inventory/api.js'
import Spinner from '@/apps/dashboard/shared/components/Spinner'
import DateRange from '@/apps/dashboard/shared/components/DateRange'

export default {
    name: "ApiChanges",
    props: {
        openTab: obj.strR,
        defaultStartTimestamp: obj.numN,
        defaultEndTimestamp: obj.numN,
    },
    components: { 
        SimpleLayout, 
        CountBox, 
        ACard, 
        LineChart, 
        LayoutWithTabs,
        SimpleTable,
        ServerTable,
        SensitiveChipGroup,
        TagChipGroup,
        BatchOperation,
        Spinner,
        DateRange
    },
    data () {
        let tabList = ['New endpoints', 'New parameters']
        if (this.openTab === "parameters") {
            tabList = tabList.reverse()
        }
        return {
            tabList: tabList,
            showDialog1: false,
            showDialog2: false,
            newParamsTrend: [],
            newParametersCount: 0,
            startTimestamp: this.defaultStartTimestamp || (func.timeNow() - func.recencyPeriod),
            endTimestamp: this.defaultEndTimestamp || func.timeNow(),
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
                    text: 'Tags',
                    value: 'tags'
                },
                {
                    text: 'Sensitive Params',
                    value: 'sensitiveTags'
                },
                {
                  text: 'Access Type',
                  value: 'access_type'
                },
                {
                  text: 'Auth Type',
                  value: 'auth_type'
                },
                {
                  text: 'Last Seen',
                  value: 'last_seen'
                },
                {
                    text: constants.DISCOVERED,
                    value: 'added'
                }
            ],
            parameterHeaders: [
                {
                    text: '',
                    value: 'color',
                    showFilterMenu: false
                },
                {
                    text: 'Name',
                    value: 'name',
                    sortKey: 'param',
                    showFilterMenu: true
                },
                {
                    text: 'Type',
                    value: 'type',
                    sortKey: 'subType',
                    showFilterMenu: true
                },
                {
                    text: 'Endpoint',
                    value: 'endpoint',
                    sortKey: 'url',
                    showFilterMenu: true
                },
                {
                    text: 'Collection',
                    value: 'apiCollectionName',
                    sortKey: 'apiCollectionId',
                    showFilterMenu: true
                },
                {
                    text: 'Method',
                    value: 'method',
                    sortKey: 'method',
                    showFilterMenu: true
                },
                {
                    text: 'Location',
                    value: 'location',
                    sortKey: 'isHeader',
                    showFilterMenu: true
                },
                {
                    text: constants.DISCOVERED,
                    value: 'added',
                    sortKey: 'timestamp',
                    showFilterMenu: true
                },
                {
                  text: 'Values',
                  value: 'domain',
                  showFilterMenu: true
                }
            ]
        }
    },
    methods: {
        toHyphenatedDate(epochInMs) {
            return func.toDateStrShort(new Date(epochInMs))
        },
        
        isSubTypeSensitive(x) {
            return func.isSubTypeSensitive(x)
        },
        getColumnValueList(headerValue) {
            switch (headerValue) {
                case "method": 
                    return {
                        type: "STRING",
                        values: ["GET", "POST", "PUT", "HEAD", "OPTIONS"].map(x => {return {
                            title: x, 
                            subtitle: '',
                            value: x
                        }})
                    }

                case "timestamp": 
                    return {
                        type: "INTEGER",
                        values: {
                            min: 0,
                            max: 600
                        }
                    }

                case "apiCollectionId": 
                    return { 
                        type: "STRING", 
                        values: this.$store.state.collections.apiCollections.map(x=> {
                            return {
                                title: x.displayName,
                                subtitle: '',
                                value: x.id
                            }
                        })
                    }

                case "isHeader":
                    return {
                        type: "STRING",
                        values: [
                            {
                                title: "Headers",
                                subtitle: '',
                                value: true
                            },
                            {
                                title: "Payload",
                                subtitle: '',
                                value: false
                            },
                        ]
                    }

                case "subType": 
                    return {
                        type: "STRING",
                        values: this.data_type_names.map(x => {
                                return {
                                    title: x, 
                                    subtitle: '',
                                    value: x
                                }
                            })
                    }
                 
                default: 
                    return  {type: "SEARCH", values: []}
            }
        },
        convertToObj(x) {
            // x.param + " " + location + " " + x.method + " " + x.url + " " + apiCollectionName,
            let arr = x.split(' ')
            return {
                param: arr[0],
                method: arr[3],
                url: arr[4],
                isHeader: arr[2].toLowerCase().startsWith("header"),
                responseCode: arr[1].toLowerCase().startsWith("request") ? -1 : (+arr[1]),
                apiCollectionId: Object.entries(this.mapCollectionIdToName).find(x => arr[5] === x[1])[0]
            }
        },
        markAllSensitive (sensitive, {items}) {
            let valueSet = items.map(x => this.convertToObj(x.value))
            api.bulkMarkSensitive(sensitive, valueSet).then(resp => {
                window._AKTO.$emit('SHOW_SNACKBAR', {
                    show: true,
                    text: `${items.length}` + ` items ${sensitive ? '':'un'}marked sensitive`,
                    color: 'green'
                })
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
        prepareItemForTable(x) {
            let idToNameMap = this.mapCollectionIdToName
            return {
                color: func.isSubTypeSensitive(x) ? this.$vuetify.theme.themes.dark.redMetric : this.$vuetify.theme.themes.dark.greenMetric,
                name: x.param.replaceAll("#", ".").replaceAll(".$", ""),
                endpoint: x.url,
                url: x.url,
                method: x.method,
                added: func.prettifyEpoch(x.timestamp),
                location: (x.responseCode === -1 ? 'Request' : 'Response') + ' ' + (x.isHeader ? 'headers' : 'payload'),
                type: x.subType.name,
                detectedTs: x.timestamp,
                apiCollectionId: x.apiCollectionId,
                apiCollectionName: idToNameMap[x.apiCollectionId] || '-',
                x: x,
                domain: func.prepareDomain(x),
                valuesString: func.prepareValuesTooltip(x)
            }
        },
        async fetchRecentParams(sortKey, sortOrder, skip, limit, filters, filterOperators) {
            return await api.fetchChanges(sortKey, sortOrder, skip, limit, filters, filterOperators, this.startTimestamp, this.endTimestamp, false, false)
        },
        async fetchSensitiveParams() {
            return await inventorApi.listAllSensitiveFields().then(resp => {
                return {endpoints: resp.data}
            })
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
                this.$store.dispatch('changes/loadRecentEndpoints', {startTimestamp: this.startTimestamp, endTimestamp: this.endTimestamp})
                api.fetchNewParametersTrend(this.startTimestamp, this.endTimestamp).then(resp => {
                    let newParametersCount = 0
                    let todayDate = new Date(this.endTimestamp * 1000)
                    let twoMonthsAgo = new Date(this.startTimestamp * 1000)
                    
                    let currDate = twoMonthsAgo
                    let ret = []
                    let dateToCount = resp.reduce((m, e) => { 
                        let detectDate = func.toYMD(new Date(e._id*86400*1000))
                        m[detectDate] = (m[detectDate] || 0 ) + e.count
                        newParametersCount += e.count
                        return m
                    }, {})
                    while (currDate <= todayDate) {
                        ret.push([func.toDate(func.toYMD(currDate)), dateToCount[func.toYMD(currDate)] || 0])
                        currDate = func.incrDays(currDate, 1)
                    }
                    this.newParametersCount = newParametersCount
                    this.newParamsTrend = ret
                })
                this.$store.dispatch('changes/fetchDataTypeNames')
            }
        },
        changesTrend (data) {
            let todayDate = new Date(this.endTimestamp * 1000)
            let twoMonthsAgo = new Date(this.startTimestamp * 1000)
            
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
        ...mapState('changes', ['apiCollection', 'apiInfoList', 'lastFetched', 'sensitiveParams', 'loading', , 'data_type_names']),
        mapCollectionIdToName() {
            return this.$store.state.collections.apiCollections.reduce((m, e) => {
                m[e.id] = e.displayName
                return m
            }, {})
        },
        newEndpoints() {
            return func.mergeApiInfoAndApiCollection(this.apiCollection, this.apiInfoList, this.mapCollectionIdToName)
        },
        newEndpointsTrend() {
            return [{
                "data": this.changesTrend(this.newEndpoints),
                "color" : "6200EA",
                "name": "New Endpoints"
            }]
        },
        newSensitiveEndpoints() {
            return this.newEndpoints.filter(x => x.sensitive && x.sensitive.size > 0)
        },
        newSensitiveParameters() {
            let now = func.timeNow()
            return this.sensitiveParams.filter(x => x.timestamp > this.startTimestamp && x.timestamp < this.endTimestamp && func.isSubTypeSensitive(x))
        },
        dateRange: {
            get () {
                return [this.toHyphenatedDate(this.startTimestamp * 1000), this.toHyphenatedDate(this.endTimestamp * 1000)]
            },
            set(newDateRange) {
                this.startTimestamp = parseInt(func.toEpochInMs(newDateRange[0]) / 1000)
                this.endTimestamp = parseInt(func.toEpochInMs(newDateRange[1]) / 1000)
                this.refreshPage(true)
            }
        }
    },
    mounted() {
        this.refreshPage(true)
    }    

}
</script>

<style lang="sass" scoped>
    .v-tooltip__content
        font-size: 15px !important
</style>