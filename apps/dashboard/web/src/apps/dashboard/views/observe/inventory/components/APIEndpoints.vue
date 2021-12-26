<template>
    <div class="pt-4 pr-4 api-endpoints">
        <div class="d-flex">
            <count-box title="Sensitive Endpoints" :count="sensitiveEndpoints.length" colorTitle="Overdue"/>
            <count-box title="Shadow Endpoints" :count="shadowEndpoints.length" colorTitle="Pending"/>
            <count-box title="Unused Endpoints" :count="unusedEndpoints.length" colorTitle="This week"/>
            <count-box title="All Endpoints" :count="allEndpoints.length" colorTitle="Total"/>
        </div>    

        <layout-with-tabs title="" :tabs="['All', 'Sensitive', 'Shadow', 'Unused']">
            <template slot="All">
                <simple-table 
                    :headers=tableHeaders 
                    :items=allEndpoints 
                    @rowClicked=rowClicked 
                    name="All" 
                    sortKeyDefault="sensitive" 
                    :sortDescDefault="true"
                >
                    <template #item.sensitive="{item}">
                        <sensitive-chip-group :sensitiveTags="Array.from(item.sensitiveTags || new Set())" />
                    </template>
                </simple-table>
            </template>
            <template slot="Sensitive">
                <simple-table 
                    :headers=tableHeaders 
                    :items=sensitiveEndpoints 
                    @rowClicked=rowClicked name="Sensitive"
                >
                    <template #item.sensitive="{item}">
                        <sensitive-chip-group :sensitiveTags="Array.from(item.sensitiveTags || new Set())" />
                    </template>
                </simple-table>
            </template>
            <template slot="Shadow">
                <simple-table 
                    :headers=tableHeaders 
                    :items=shadowEndpoints 
                    @rowClicked=rowClicked 
                    name="Shadow"  
                    sortKeyDefault="sensitive" 
                    :sortDescDefault="true"
                >
                    <template #item.sensitive="{item}">
                        <sensitive-chip-group :sensitiveTags="Array.from(item.sensitiveTags || new Set())" />
                    </template>
                </simple-table>
            </template>
            <template slot="Unused">
                <simple-table 
                    :headers=unusedHeaders 
                    :items=unusedEndpoints 
                    name="Unused"
                />
            </template>
        </layout-with-tabs>

    </div>
</template>

<script>
import CountBox from '@/apps/dashboard/shared/components/CountBox'
import { mapState } from 'vuex'
import func from "@/util/func"
import constants from '@/util/constants'
import LayoutWithTabs from '@/apps/dashboard/layouts/LayoutWithTabs'
import SimpleTable from '@/apps/dashboard/shared/components/SimpleTable'
import api from '../api'
import SensitiveChipGroup from './SensitiveChipGroup.vue'

export default {
    name: "ApiEndpoints",
    components: { 
        CountBox, 
        LayoutWithTabs,
        SimpleTable,
        SensitiveChipGroup        
    },
    data() {
        return {
            tableHeaders: [
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
                },
                {
                    text: 'Changes',
                    value: 'changes',
                    sortKey: 'changesCount'
                }
            ],
            unusedHeaders: [
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
                }
            ],
            documentedURLs: {}
        }
    },
    methods: {
        rowClicked(row) {
            this.$emit('selectedItem', {apiCollectionId: this.apiCollectionId || 0, urlAndMethod: row.endpoint + " " + row.method, type: 2})
        },
        groupByEndpoint(listParams) {
            func.groupByEndpoint(listParams)
        },
        prettifyDate(ts) {
            if (ts)
                return func.prettifyEpoch(ts)
            else
                return '-'
        },
        isShadow(x) {
            return !(this.documentedURLs[x.endpoint] && this.documentedURLs[x.endpoint].indexOf(x.method) != -1)
        },
        isUnused(url, method) {
            return this.allEndpoints.filter(e => e.endpoint === url && e.method == method).length == 0
        }
    },
    computed: {
        ...mapState('inventory', ['apiCollection', 'apiCollectionName', 'apiCollectionId']),
        allEndpoints () {
            return func.groupByEndpoint(this.apiCollection)
        },
        sensitiveEndpoints() {
            return func.groupByEndpoint(this.apiCollection).filter(x => x.sensitive > 0)
        },
        shadowEndpoints () {
            return func.groupByEndpoint(this.apiCollection).filter(x => this.isShadow(x))
        },
        unusedEndpoints () {
            let ret = []
            Object.entries(this.documentedURLs).forEach(entry => {
                let endpoint = entry[0]
                entry[1].forEach(method => {
                    if(this.isUnused(endpoint, method)) {
                        ret.push({
                            endpoint, 
                            method,
                            color: func.actionItemColors()["This week"]
                        })
                    }
                })
            })
            return ret
        }
    },
    mounted() {
        api.getAllUrlsAndMethods(this.apiCollectionId).then(resp => {
            this.documentedURLs = resp.data || {}
        })
    }

}
</script>

<style lang="sass">
.api-endpoints
    & .table-column
        &:nth-child(1)    
            width: 4px
            min-width: 4px
            max-width: 4px
        &:nth-child(2)    
            width: 350px
            min-width: 350px
        &:nth-child(3)    
            width: 150px
            min-width: 150px
            max-width: 150px
        &:nth-child(4)    
            width: 250px
            min-width: 250px
            max-width: 250px
        &:nth-child(5)    
            width: 200px
            min-width: 200px
            max-width: 200px
        &:nth-child(6)    
            width: 200px
            min-width: 200px
            max-width: 200px

</style>