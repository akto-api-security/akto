<template>
    <div>        
        <simple-layout title="Sensitive Data"/>          
        <div class="d-flex pa-4">
            <count-box title="Total sensitive parameters" :count="allSensitiveParams.length" colorTitle="Overdue"/>
            <count-box title="New sensitive parameters" :count="newSensitiveParams.length" colorTitle="Total"/>
        </div>
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
                                color="#47466A" 
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
                <simple-table 
                    :headers=headers 
                    :items=sensitiveParamsInRequestForTable 
                    name="Request"
                    @rowClicked="goToEndpoint"
                >
                    <template #item.type="{item}">
                        <sensitive-chip-group :sensitiveTags="[item.type]" />
                    </template>
                </simple-table>
            </template>
            <template slot="Response">
                <simple-table 
                    :headers=headers 
                    :items=sensitiveParamsInResponseForTable 
                    name="Response"
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
import LayoutWithTabs from '@/apps/dashboard/layouts/LayoutWithTabs'
import CountBox from '@/apps/dashboard/shared/components/CountBox'
import SimpleTable from '@/apps/dashboard/shared/components/SimpleTable'
import SensitiveParamsCard from '@/apps/dashboard/shared/components/SensitiveParamsCard'
import { mapState } from 'vuex'
import func from '@/util/func'
import constants from '@/util/constants'
import SimpleLayout from '@/apps/dashboard/layouts/SimpleLayout'
import SensitiveChipGroup from '@/apps/dashboard/shared/components/SensitiveChipGroup'

export default {
    name: "SensitiveData",
    components: {
        CountBox,
        LayoutWithTabs,
        SimpleTable,
        SensitiveParamsCard,
        SimpleLayout,
        SensitiveChipGroup 
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
                    text: constants.DISCOVERED,
                    value: 'added',
                    sortKey: 'detectedTs'
                },
                {
                    text: 'Location',
                    value: 'location'
                }
            ]
        }
    },
    methods: {
        prepareItemForTable(x) {
            return {
                color: this.$vuetify.theme.themes.dark.redMetric,
                name: x.param.replaceAll("#", ".").replaceAll(".$", ""),
                endpoint: x.url,
                method: x.method,
                added: this.prettifyDate(x.timestamp),
                location: x.isHeader ? 'Headers' : 'Payload',
                type: x.subType.name || "OTHER",
                apiCollectionId: x.apiCollectionId,
                apiCollectionName: this.mapCollectionIdToName[x.apiCollectionId] || '-'
            }
        },
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
                    color: x[0] === 'General' ? "#7D787838" : (["#6200EAFF", "#6200EADF", "#6200EABF", "#6200EA9F", "#6200EA7F", "#6200EA5F", "#6200EA3F", "#6200EA1F"][i])
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
        refreshPage(shouldLoad) {
            this.$store.dispatch('sensitive/loadSensitiveParameters', {shouldLoad:shouldLoad})
        }
    },
    computed: {
        ...mapState('sensitive', ['apiCollection']),
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
            return this.sensitiveParamsForChart(this.apiCollection.filter(x => x.responseCode == -1))
        },
        sensitiveParamsInResponseForChart() {
            return this.sensitiveParamsForChart(this.apiCollection.filter(x => x.responseCode > -1))
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