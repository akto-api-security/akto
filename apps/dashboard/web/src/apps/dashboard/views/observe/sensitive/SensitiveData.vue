<template>
    <div>        
        <simple-layout title="Sensitive Data"/>          
        <!-- <div class="d-flex pa-4">
            <count-box title="Total sensitive parameters" :count="allSensitiveParams.length" colorTitle="Overdue"/>
            <count-box title="New sensitive parameters" :count="newSensitiveParams.length" colorTitle="Total"/>
        </div> -->
        <div class="d-flex pa-4">
            <sensitive-params-card title="Sensitive parameters in response" :sensitiveParams="sensitiveParamsInResponseForChart"/>
            <sensitive-params-card title="Sensitive parameters in request" :sensitiveParams="sensitiveParamsInRequestForChart"/>
        </div> 
        <layout-with-tabs :tabs="['Response', 'Request']">
            <template slot="actions-tray">
                <div class="d-flex jc-end">
                    <v-tooltip bottom>
                        <template v-slot:activator='{on, attrs}'>
                            <v-btn 
                                icon 
                                color="var(--themeColorDark)" 
                                @click="refreshPage(false)"
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
            <template slot="Request">
                <server-table 
                    :headers="parameterHeaders" 
                    name="Request" 
                    sortKeyDefault="timestamp" 
                    :sortDescDefault="true"
                    @rowClicked="goToEndpoint"
                    :fetchParams="fetchRecentParamsForRequest"
                    :processParams="prepareItemForTable"
                    :getColumnValueList="getColumnValueList"
                    :hideDownloadCSVIcon="true"
                >
                    <template #item.type="{item}">
                        <sensitive-chip-group :sensitiveTags="[item.type]" />
                    </template>
                </server-table>
            </template>
            <template slot="Response">
                <server-table 
                    :headers="parameterHeaders" 
                    name="Response" 
                    sortKeyDefault="timestamp" 
                    :sortDescDefault="true"
                    @rowClicked="goToEndpoint"
                    :fetchParams="fetchRecentParamsForResponse"
                    :processParams="prepareItemForTable"
                    :getColumnValueList="getColumnValueList"
                    :hideDownloadCSVIcon="true"
                >
                    <template #item.type="{item}">
                        <sensitive-chip-group :sensitiveTags="[item.type]" />
                    </template>
                </server-table>
            </template>
        </layout-with-tabs>  
    </div>
</template>

<script>
import LayoutWithTabs from '@/apps/dashboard/layouts/LayoutWithTabs'
import CountBox from '@/apps/dashboard/shared/components/CountBox'
import SimpleTable from '@/apps/dashboard/shared/components/SimpleTable'
import SensitiveParamsCard from '@/apps/dashboard/shared/components/SensitiveParamsCard'
import { mapState } from 'vuex'
import func from '@/util/func'
import constants from '@/util/constants'
import SimpleLayout from '@/apps/dashboard/layouts/SimpleLayout'
import SensitiveChipGroup from '@/apps/dashboard/shared/components/SensitiveChipGroup'
import obj from '@/util/obj'
import api from '@/apps/dashboard/views/observe/changes/api.js'
import ServerTable from '@/apps/dashboard/shared/components/ServerTable'

export default {
    name: "SensitiveData",
    components: {
        CountBox,
        LayoutWithTabs,
        SimpleTable,
        SensitiveParamsCard,
        SimpleLayout,
        SensitiveChipGroup,
        ServerTable
    },
    props: {
        subType: obj.strN
    },
    data () {
 
           return {
            startTimestamp: (func.timeNow() - func.recencyPeriod),
            endTimestamp: func.timeNow(),
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
                    showFilterMenu: false
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
                    showFilterMenu: false
                },
                {
                    text: 'Collection',
                    value: 'apiCollectionName',
                    sortKey: 'apiCollectionId',
                    showFilterMenu: false
                },
                {
                    text: 'Method',
                    value: 'method',
                    sortKey: 'method',
                    showFilterMenu: false
                },
                {
                    text: 'Location',
                    value: 'location',
                    sortKey: 'isHeader',
                    showFilterMenu: false
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
                  showFilterMenu: false
                }
            ],
            actions: [ 
                {
                    isValid: item => true,
                    icon: item => '$trashSingleTick',
                    text: item => 'Ignore key for this API',
                    func: item => this.ignoreForThisAPI(item),
                    success: (resp, item) => this.successfullyIgnored(resp, item),
                    failure: (err, item) => this.unsuccessfullyIgnored(err, item)
                },
                {
                    isValid: item => true,
                    icon: item => '$trashDoubleTick',
                    text: item => 'Ignore key for all APIs',
                    func: item => this.ignoreForAllAPIs(item),
                    success: (resp, item) => this.successfullyIgnored(resp, item),
                    failure: (err, item) => this.unsuccessfullyIgnored(err, item)
                }
            ],
            subTypeCountMap: {}
        }
    },
    methods: {
        async fetchRecentParamsForRequest(sortKey, sortOrder, skip, limit, filters, filterOperators) {
            return await api.fetchChanges(sortKey, sortOrder, skip, limit, filters, filterOperators, 0, this.endTimestamp, true, true)
        },
        async fetchRecentParamsForResponse(sortKey, sortOrder, skip, limit, filters, filterOperators) {
            return await api.fetchChanges(sortKey, sortOrder, skip, limit, filters, filterOperators, 0, this.endTimestamp, true, false)
        },
        ignoreForThisAPI(item) {
            this.ignoredCollection = item.name
            if(confirm("Ignore key for this API ?")) {
                const summ = this.$store.dispatch('sensitive/ignoreForThisApi', {apiCollection: item})
                console.log(summ)
                return summ
            }
        },
        ignoreForAllAPIs(item) {
            this.ignoredCollection = item.name
            if(confirm("Ignore key for all APIs ?")) {
                const summ = this.$store.dispatch('sensitive/ignoreForAllApis', {apiCollection: item})
                console.log(summ)
                return summ
            }
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
        successfullyIgnored(resp,item) {
            window._AKTO.$emit('SHOW_SNACKBAR', {
                show: true,
                text: `${this.ignoredCollection}` + ` ignored successfully!`,
                color: 'green'
            })
            this.ignoredCollection = null
        },
        unsuccessfullyIgnored(resp,item) {
            window._AKTO.$emit('SHOW_SNACKBAR', {
                show: true,
                text: `${this.ignoredCollection}` + ` could not be ignored`,
                color: 'red'
            })
            this.ignoredCollection = null
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
            }        },
        prettifyDate(ts) {
            if (ts) {
                return func.prettifyEpoch(ts)
            } else {
                return '-'
            }
        },
        sensitiveParamsForChart(allParams) {
            return Object.entries((allParams || []).reduce((z, e) => {
                let key = func.isSubTypeSensitive(e) ? e.subType.name : 'General'
                z[key] = (z[key] || 0) + 1
                return z
            }, {})).map((x, i) => {
                return {
                    name: x[0],
                    y: x[1],
                    color: x[0] === 'General' ? "var(--hexColor16)" : (["var(--themeColor)", "var(--themeColor2)", "var(--themeColor3)", "var(--themeColor4)", "var(--themeColor6)", "var(--themeColor7)", "var(--themeColor8)", "var(--themeColor11)"][i])
                }
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
                this.$store.dispatch('changes/fetchDataTypeNames')
                api.fetchSubTypeCountMap(0, this.endTimestamp).then((resp)=> {
                    this.subTypeCountMap = resp.response.subTypeCountMap
                })
            }
        },
        buildPieChart(countMap) {
            if (!countMap) return []
            return Object.entries(countMap).sort((a, b) => b[1] - a[1]).map((x,i) => {
                return {
                    "name": x[0],
                    "y": x[1],
                    "color": x[0] === 'General' ? "var(--hexColor16)" : (["var(--themeColor)", "var(--themeColor2)", "var(--themeColor3)", "var(--themeColor4)", "var(--themeColor6)", "var(--themeColor7)", "var(--themeColor8)", "var(--themeColor11)"][i])
                }
            })
        }
    },
    computed: {
        ...mapState('changes', ['apiCollection', 'apiInfoList', 'lastFetched', 'sensitiveParams', 'loading', 'data_type_names']),
        mapCollectionIdToName() {
            return this.$store.state.collections.apiCollections.reduce((m, e) => {
                m[e.id] = e.name
                return m
            }, {})
        },
        sensitiveParamsInRequestForTable() {
            return this.apiCollection.filter(x => x.responseCode == -1 && func.isSubTypeSensitive(x)).map(this.prepareItemForTable)
        },
        sensitiveParamsInResponseForTable() {
            return this.apiCollection.filter(x => x.responseCode > -1 && func.isSubTypeSensitive(x)).map(this.prepareItemForTable)
        },
        sensitiveParamsInRequestForChart() {
            let requestSubTypeCountMap = this.subTypeCountMap["REQUEST"]
            return this.buildPieChart(requestSubTypeCountMap)
        },
        sensitiveParamsInResponseForChart() {
            let responseSubTypeCountMap = this.subTypeCountMap["RESPONSE"]
            return this.buildPieChart(responseSubTypeCountMap)
        },
        allSensitiveParams() {
           return this.apiCollection.filter(x => func.isSubTypeSensitive(x))
        },
        newSensitiveParams() {
           let now = func.timeNow()
           return this.apiCollection.filter(x => func.isSubTypeSensitive(x) && x.timestamp > (now - func.recencyPeriod))
        }              
    },
    mounted() {
        this.refreshPage(true)
    }
}
</script>

<style lang="sass" scoped>

</style>