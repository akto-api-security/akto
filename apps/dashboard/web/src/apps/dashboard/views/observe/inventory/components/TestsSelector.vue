<template>
    <spinner v-if="loading"/>
    <a-card  v-else title="Configure test" icon="$fas_cog" class="tests-selector-container">
        <div class="mx-8 my-4">
            <div v-for="(value, key, index) in mapCategoryToSubcategory" :key="index" >
                <v-select
                    v-model="value.selected"
                    :items="value.all"
                    item-text="label"
                    item-value="value"
                    :label=key
                    multiple
                    chips
                />
            </div>

            <schedule-box @schedule="emitTestSelection"/>

        </div>
    </a-card>
</template>

<script>

import marketplaceApi from '../../../marketplace/api'
import issuesApi from '../../../issues/api'
import Spinner from '@/apps/dashboard/shared/components/Spinner'
import ScheduleBox from '@/apps/dashboard/shared/components/ScheduleBox'
import func from '@/util/func'
import ACard from '@/apps/dashboard/shared/components/ACard'

export default {
    name: "TestsSelector",
    components: {
        ScheduleBox,
        Spinner,
        ACard
    },
    data () {
        return {
            testSourceConfigs: [],
            businessLogicCategories: [],
            loading: false,
            mapCategoryToSubcategory: {},
            recurringDaily: false,
            startTimestamp: func.timeNow()
        }
    },
    mounted() {
        let _this = this
        marketplaceApi.fetchAllMarketplaceSubcategories().then(resp => {
            _this.testSourceConfigs = resp.testSourceConfigs
            issuesApi.fetchAllSubCategories().then(resp => {
                _this.businessLogicCategories = resp.subCategories
                _this.loading = false
                _this.mapCategoryToSubcategory = _this.populateMapCategoryToSubcategory()
            })
        })
        
        
    },
    methods: {
        emitTestSelection({recurringDaily, startTimestamp}) {
            this.recurringDaily = recurringDaily
            this.startTimestamp = startTimestamp
            let selectedTests = Object.values(this.mapCategoryToSubcategory).map(x => x.selected)
            let ret = {recurringDaily: this.recurringDaily, startTimestamp: this.startTimestamp, selectedTests}
            console.log('testsSelected', ret)
            return this.$emit('testsSelected', ret)
        },
        populateMapCategoryToSubcategory() {
            let ret = {}
            this.testSourceConfigs.forEach(x => {
                if (!ret[x.category]) {
                    ret[x.category] = {selected: [], all: []}
                }

                ret[x.category].all.push({label: x.id.substring(x.id.lastIndexOf("/")+1, x.id.lastIndexOf(".")), value: x.id});
            })

            this.businessLogicCategories.forEach(x => {
                if (!ret[x.superCategory.name]) {
                    ret[x.superCategory.name] = {selected: [], all: []}
                }
                ret[x.superCategory.name].all.push({label: x.name.toLowerCase(), value: x.name})
            })

            return ret
        }
    }
    
}
</script>

<style lang="sass" scoped>
.tests-selector-container
    width: 800px
    background-color: #FFFFFF
    margin: 0px !important
</style>