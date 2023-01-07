<template>
    <spinner v-if="loading"/>
    <a-card  v-else title="Configure test" icon="$fas_cog" class="tests-selector-container">
        <div class="mx-8 my-4">
            <div class="d-flex brda">
                <div class="category-list-container">
                    <v-list-item class="brdr">
                        <div class="column-title">
                            Test Categories
                        </div>
                    </v-list-item>
                    <v-list dense class="category-list pa-0 fd-column js-sb">
                        <v-list-item v-for="(value, category, index) in mapCategoryToSubcategory" :key="index"  :class="['clickable', 'brdt', 'brdr' ,'category-item', selectedCategory == category ?  'selected-category' : '']"  @click="selectedCategory = category; globalCheckbox = false">
                            <div class="d-flex jc-sb" style="width: 100%">
                                <div>
                                    <div class="fw-500">{{category}}</div>
                                    <div class="grey-text fs-12">{{mapCategoryToSubcategory[category].selected.length}} of {{mapCategoryToSubcategory[category].all.length}} selected</div>
                                </div>
                                <v-icon v-if="mapCategoryToSubcategory[category].selected.length > 0" size="16" color="#6200EA">$fas_check</v-icon>
                            </div>
                        </v-list-item>
                    </v-list>
                </div>
                <div class="test-list-container">
                    <v-list-item class="brdb">
                        <div class="pa-0 column-title">
                            <v-btn icon plain color="#47466A" size="12" @click="globalCheckboxClicked()" :ripple="false">
                                <v-icon>{{globalCheckbox? '$far_check-square' : '$far_square'}}</v-icon>
                            </v-btn>
                            Tests
                        </div>
                    </v-list-item>
                    <v-list dense class="test-list pa-0" v-if="selectedCategory">
                        <v-list-item v-for="(item, index) in mapCategoryToSubcategory[selectedCategory].selected" :key="'selected_'+index" class="brdb test-item">
                            <v-btn icon plain size="12" color="#47466A" @click="mapCategoryToSubcategory[selectedCategory].selected.splice(index, 1);" :ripple="false">
                                <v-icon>$far_check-square</v-icon>
                            </v-btn> 
                            <v-icon color="#47466A" size="12">{{item.icon}}</v-icon>
                            {{item.label}}
                        </v-list-item>
                        <v-list-item 
                            v-for="(item, index) in mapCategoryToSubcategory[selectedCategory].all.filter(x => !mapCategoryToSubcategory[selectedCategory].selected.find(y => x.label === y.label))" 
                            :key="'all_'+index" 
                            class="brdb test-item"
                        >
                                <v-btn icon plain size="12" color="#47466A" @click="mapCategoryToSubcategory[selectedCategory].selected.push(item)" :ripple="false">
                                    <v-icon>$far_square</v-icon>
                                </v-btn> 
                                <v-icon color="#47466A" size="12">{{item.icon}}</v-icon>
                                {{item.label}}
                        </v-list-item>
                    </v-list>
                </div>
            </div>

            <schedule-box @schedule="emitTestSelection" class="mt-2"/>

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
            startTimestamp: func.timeNow(),
            selectedCategory: null,
            globalCheckbox: false
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
        globalCheckboxClicked() {
            this.globalCheckbox = !this.globalCheckbox
            let currObj = this.mapCategoryToSubcategory[this.selectedCategory]
            currObj.selected = this.globalCheckbox ? [...currObj.all] : []
        },
        emitTestSelection({recurringDaily, startTimestamp}) {
            this.recurringDaily = recurringDaily
            this.startTimestamp = startTimestamp
            let selectedTests = Object.values(this.mapCategoryToSubcategory).map(x => x.selected).flat().map(x => x.value)
            let ret = {recurringDaily: this.recurringDaily, startTimestamp: this.startTimestamp, selectedTests}
            return this.$emit('testsSelected', ret)
        },
        populateMapCategoryToSubcategory() {
            let ret = {}
            this.testSourceConfigs.forEach(x => {
                if (!ret[x.category]) {
                    ret[x.category] = {selected: [], all: []}
                }

                let obj = {
                    label: x.id.substring(x.id.lastIndexOf("/")+1, x.id.lastIndexOf(".")), 
                    value: x.id,
                    icon: "$fab_github"
                }
                ret[x.category].all.push(obj);
            })

            this.businessLogicCategories.forEach(x => {
                if (!ret[x.superCategory.name]) {
                    ret[x.superCategory.name] = {selected: [], all: []}
                }

                let obj = {
                    label: x.name.toLowerCase().replaceAll("_", " "),
                    value: x.name,
                    icon: "$aktoWhite"
                }
                ret[x.superCategory.name].all.push(obj)
                ret[x.superCategory.name].selected.push(obj)
            })
            this.selectedCategory = Object.keys(ret)[0]
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
    color: #47466A

.item-title
    font-size: 12px
    padding: 4px !important    

.check-box
    max-height: 20px

.list-item
    min-height: unset !important    

.btn-icon
    height: 24px !important
    width: 24px !important  

.test-list-container
    flex-grow: 1    

.category-list-container
    width: 40%
    flex-grow: 0

.test-list
    height: 400px
    overflow: scroll

.category-list
    height: 400px
    overflow: scroll

.column-title
    padding: 0px 8px
    font-size: 12px
    font-weight: 500
    color: #47466A    
        
.category-item
    padding: 16px 24px
    color: #47466A !important
    font-size: 14px       
    
.test-item
    font-size: 12px
    color: #47466A !important

.selected-category
    background-color: #F4F4F4
</style>