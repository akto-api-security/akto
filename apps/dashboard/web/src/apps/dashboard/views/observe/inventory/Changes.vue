<template>
    <div>
        <simple-layout title="API Changes"/>
        <div class="d-flex pa-4">
            <count-box title="New endpoints" :count="newEndpoints.length" colorTitle="Total" />
            <count-box title="New sensitive endpoints" :count="newSensitiveEndpoints.length" colorTitle="Overdue" />
            <count-box title="New parameters" :count="newParameters.length" colorTitle="Total" />
            <count-box title="New sensitive parameters" :count="newSensitiveParameters.length" colorTitle="Overdue" />
        </div>
        <a-card title="Changes" icon="$fas_chart-line">
            <div class="pa-4 coming-soon">Coming soon...</div>
        </a-card>
        <layout-with-tabs title="" :tabs="['New endpoints', 'New parameters']">
            <template slot="New endpoints">
                <simple-table 
                    :headers="endpointHeaders" 
                    :items="newEndpoints" 
                    name="New endpoints" 
                    sortKeyDefault="added" 
                    :sortDescDefault="true" 
                />
            </template>
            <template slot="New parameters">
                <simple-table 
                    :headers="parameterHeaders" 
                    :items="newParameters" 
                    name="New parameters" 
                    sortKeyDefault="added" 
                    :sortDescDefault="true"
                />
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
        SimpleTable
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
            return {
                color: func.isSubTypeSensitive(x.subType) ? this.$vuetify.theme.themes.dark.redMetric : this.$vuetify.theme.themes.dark.greenMetric,
                name: x.param.replaceAll("#", ".").replaceAll(".$", ""),
                endpoint: x.url,
                method: x.method,
                added: func.prettifyEpoch(x.timestamp),
                location: (x.responseCode == -1 ? 'Request' : 'Response') + ' ' + (x.isHeader ? 'headers' : 'payload'),
                type: x.subType,
                detectedTs: x.timestamp
            }
        }        
    },
    computed: {
        ...mapState('inventory', ['apiCollection']),
        newEndpoints() {
            let now = func.timeNow()
            return func.groupByEndpoint(this.apiCollection).filter(x => x.detectedTs > now - func.recencyPeriod)
        },
        newParameters() {
            let now = func.timeNow()
            return this.apiCollection.filter(x => x.timestamp > now - func.recencyPeriod).map(this.prepareItemForTable)
        },
        newSensitiveEndpoints() {
            let now = func.timeNow()
            return func.groupByEndpoint(this.apiCollection).filter(x => x.detectedTs > now - func.recencyPeriod && x.sensitive > 0)
        },
        newSensitiveParameters() {
            let now = func.timeNow()
            return this.apiCollection.filter(x => x.timestamp > now - func.recencyPeriod && func.isSubTypeSensitive(x.subType)).map(this.prepareItemForTable)
        },
    },
    mounted() {
        this.$store.dispatch('inventory/loadAPICollection', { apiCollectionId: this.apiCollectionId})
    }    

}
</script>

<style lang="sass" scoped>

</style>