<template>
    <div>
        <v-row>
            <v-col md="6">
                <sensitive-params-card title="Sensitive parameters" :sensitiveParams="sensitiveParamsForChart"/>
            </v-col>
            
            <v-col md="6">
                <a-card title="Traffic" icon="$fas_chart-line">
                    <div class="pa-4 coming-soon">Coming soon...
                    </div>
                </a-card>
            </v-col>
        </v-row>
        <layout-with-tabs :tabs="['Request', 'Response']">
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
        </layout-with-tabs>
    </div>    
</template>

<script>
import SimpleTable from '@/apps/dashboard/shared/components/SimpleTable'
import ACard from '@/apps/dashboard/shared/components/ACard'
import LayoutWithTabs from '@/apps/dashboard/layouts/LayoutWithTabs'
import DonutChart from '@/apps/dashboard/shared/components/DonutChart'
import {mapState, mapGetters} from 'vuex'
import obj from '@/util/obj'
import func from '@/util/func'
import api from '../api'
import SensitiveParamsCard from './SensitiveParamsCard.vue'

export default {
    name: "ApiParameters",
    components: {
        SimpleTable,
        ACard,
        LayoutWithTabs,
        DonutChart,
        SensitiveParamsCard
    },
    props: {
        urlAndMethod: obj.strR
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
                    text: 'Added on',
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
            ]
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
                type: x.subType,
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
            return !func.isSubTypeSensitive(obj)
        }
    },
    computed: {
        ...mapState('inventory', ['apiCollection']),
        url () {
            return this.urlAndMethod.split(" ")[0]
        },
        method () {
            return this.urlAndMethod.split(" ")[1]
        },
        sensitiveParams() {
            return this.apiCollection.filter(x => x.url === this.url && x.method == this.method)
        },
        sensitiveParamsForChart() {
            return Object.entries(this.sensitiveParams.reduce((z, e) => {
                let key = func.isSubTypeSensitive(e) ? e.subType : 'General'
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
        requestItems() {
            return this.sensitiveParams.filter(x => x.responseCode == -1).map(this.prepareItem)
        },
        responseItems() {
            return this.sensitiveParams.filter(x => x.responseCode > -1).map(this.prepareItem)
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