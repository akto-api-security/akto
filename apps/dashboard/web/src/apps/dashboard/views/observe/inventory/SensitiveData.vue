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
            <template slot="Request">
                <simple-table :headers=headers :items=sensitiveParamsInRequestForTable name="Request"/>
            </template>
            <template slot="Response">
                <simple-table :headers=headers :items=sensitiveParamsInResponseForTable name="Response" />
            </template>
        </layout-with-tabs>  
    </div>
</template>

<script>
import LayoutWithTabs from '@/apps/dashboard/layouts/LayoutWithTabs'
import CountBox from '@/apps/dashboard/shared/components/CountBox'
import SimpleTable from '@/apps/dashboard/shared/components/SimpleTable'
import SensitiveParamsCard from './components/SensitiveParamsCard'
import { mapState } from 'vuex'
import func from '@/util/func'
import SimpleLayout from '@/apps/dashboard/layouts/SimpleLayout'

export default {
    name: "SensitiveData",
    components: {
        CountBox,
        LayoutWithTabs,
        SimpleTable,
        SensitiveParamsCard,
        SimpleLayout        
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
                    text: 'Method',
                    value: 'method'
                },
                {
                    text: 'Added',
                    value: 'added'
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
                type: x.subType
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
                let key = func.isSubTypeSensitive(e.subType) ? e.subType : 'General'
                z[key] = (z[key] || 0) + 1
                return z
            }, {})).map((x, i) => {
                return {
                    name: x[0],
                    y: x[1],
                    color: x[0] === 'General' ? "#7D787838" : (["#6200EAFF", "#6200EADF", "#6200EABF", "#6200EA9F", "#6200EA7F", "#6200EA5F", "#6200EA3F", "#6200EA1F"][i])
                }
            })
        }
    },
    computed: {
        ...mapState('inventory', ['apiCollection']),
        sensitiveParamsInRequestForTable() {
            return this.apiCollection.filter(x => x.responseCode == -1 && func.isSubTypeSensitive(x.subType)).map(this.prepareItemForTable)
        },
        sensitiveParamsInResponseForTable() {
            return this.apiCollection.filter(x => x.responseCode > -1 && func.isSubTypeSensitive(x.subType)).map(this.prepareItemForTable)
        },
        sensitiveParamsInRequestForChart() {
            return this.sensitiveParamsForChart(this.apiCollection.filter(x => x.responseCode == -1))
        },
        sensitiveParamsInResponseForChart() {
            return this.sensitiveParamsForChart(this.apiCollection.filter(x => x.responseCode > -1))
        },
        allSensitiveParams() {
           return this.apiCollection.filter(x => func.isSubTypeSensitive(x.subType))
        },
        newSensitiveParams() {
           let now = func.timeNow()
           return this.apiCollection.filter(x => func.isSubTypeSensitive(x.subType) && x.timestamp > (now - 15 * 24 * 60 * 60))
        }              
    },
    mounted() {
        this.$store.dispatch('inventory/loadAPICollection', { apiCollectionId: this.apiCollectionId})
    }
}
</script>

<style lang="sass" scoped>

</style>