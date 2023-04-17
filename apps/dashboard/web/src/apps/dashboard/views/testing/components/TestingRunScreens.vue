<template>
    <div :class="['screens-container', !showTestScreen ? 'no-display' : '']">
        <div class="screen-header">
            <span>
                {{ item.displayName }}
                <v-icon size="16">$fas_pen</v-icon>
            </span>
            <secondary-button class="screen-buttons" text="Close" @click="toggle()" />
        </div>

        <div class="screen-body">
            <v-stepper v-model="currIndex" class="stepper">
                <v-stepper-header class="stepper-header">
                    <template v-for="(item,index) in stepperData">
                        <v-stepper-step
                            :complete="currIndex > (index + 1)"
                            :step="index + 1"
                            complete-icon="$fas_check"
                            :key="item"
                            class="stepper-title"
                        >
                            {{ item }}
                        </v-stepper-step>
                        <v-divider v-if="index < (stepperData.length - 1)" :key="index + 1" class="divider"/>
                    </template>
                </v-stepper-header>

                <v-stepper-content step="1">
                    <div>
                        <span class="collection-text">Select Collections</span>
                        <div v-if="total_collections > 0" class="selected-text">
                            <span class="bold-text">{{ total_collections }} Collections with {{ totalEndpoints }} Endpoints </span>
                            <span>match these conditions</span>
                        </div>
                    </div>
                    <test-roles-condition :collection-name="collectionName" @collections-selected="getCollections"/>
                </v-stepper-content>

                <v-stepper-content step="2">
                    <tests-selector 
                        :hide-test-scheduler="true" 
                        collection-name="Tests-Selector" 
                        @test-updated="getTestsSelected"
                    />
                </v-stepper-content>

                <v-stepper-content step="3">
                    <user-config/>
                </v-stepper-content>

                <v-stepper-content step="4">
                    <span class="summary-text">Summary</span>
                    <div class="summary-container">
                        <div class="summary-boxes">
                            <v-icon size="30">$aktoWhite</v-icon>
                            <div class="summary-body">
                                <div class="summary-header">
                                    <span class="header-title">{{ total_collections }} collections selected</span>
                                    <v-btn icon plain @click="showCollectionSummary = !showCollectionSummary">
                                        <v-icon size="20" :style="{transform: showCollectionSummary ? 'rotate(180deg)' : '', transition: 'all 0.2s linear'}">$fas_angle-down</v-icon>
                                    </v-btn>
                                </div>
                                <span class="summary-collection-text">{{ totalEndpoints }} Endpoints</span>
                            </div>
                        </div>

                        <div class="summary-list" v-if="showCollectionSummary">
                            <v-list dense nav class="test-list-container">
                                <v-list-group
                                    v-for="item in selectedCollection"
                                    :key="item"
                                    class="tests-category-container"
                                    active-class="tests-category-container-active"
                                >
                                    <template v-slot:appendIcon>
                                        <v-icon >$fas_angle-down</v-icon>
                                    </template>

                                    <template v-slot:prependIcon>
                                        <v-icon >$far_folder</v-icon>
                                    </template>
                                    <template v-slot:activator>
                                        <v-list-item-content>
                                            <v-list-item-title :style="{'font-size' : '16px'}" v-text="item"></v-list-item-title>
                                        </v-list-item-content>
                                    </template>

                                    <v-list-item
                                        v-for="(coll,index) in endPointsTaken[item]"
                                        :key="index"
                                        class="test-container"
                                    >
                                    
                                        <v-list-item-content>
                                            <v-list-item-title v-text="coll.url" class="test-name"></v-list-item-title>
                                        </v-list-item-content>
                                    </v-list-item>
                                </v-list-group>
                            </v-list>
                        </div>
    
                        <div class="summary-boxes">
                            <v-icon size="30">$aktoWhite</v-icon>
                            <div class="summary-body">
                                <div class="summary-header">
                                    <span class="header-title">{{ totalTestsSelected }} tests selected</span>
                                    <v-btn icon plain @click="showTestSummary = !showTestSummary">
                                        <v-icon size="20" :style="{transform: showTestSummary ? 'rotate(180deg)' : '', transition: 'all 0.2s linear'}">$fas_angle-down</v-icon>
                                    </v-btn>
                                </div>
                                <div class="button-containers">
                                    <template v-for="(item) in selectedTestCategories">
                                        <div class="test-categories" :key="item.name">
                                            <span>{{ item.name }}</span>
                                        </div>
                                    </template>
                                </div>
                            </div>
                        </div>

                        <div class="summary-list" v-if="showTestSummary">
                            <v-list dense nav class="test-list-container">
                                <v-list-group
                                    v-for="item in selectedTestCategories"
                                    :key="item.displayName"
                                    class="tests-category-container"
                                    active-class="tests-category-container-active"
                                >
                                    <template v-slot:appendIcon>
                                        <v-icon >$fas_angle-down</v-icon>
                                    </template>

                                    <template v-slot:prependIcon>
                                        <v-icon >$fas_cog</v-icon>
                                    </template>
                                    <template v-slot:activator>
                                        <v-list-item-content>
                                            <v-list-item-title :style="{'font-size' : '16px'}" v-text="item.displayName"></v-list-item-title>
                                        </v-list-item-content>
                                    </template>

                                    <v-list-item
                                        v-for="(test,index) in testsSelected[item.name].selected"
                                        :key="index"
                                        class="test-container"
                                    >
                                    
                                        <v-list-item-content>
                                            <v-list-item-title v-text="test.label" class="test-name"></v-list-item-title>
                                        </v-list-item-content>
                                    </v-list-item>
                                </v-list-group>
                            </v-list>
                        </div>
                    </div>
                </v-stepper-content>

            </v-stepper>
        </div>

        <div class="screen-footer">
            <div class="d-flex jc-end">
                <secondary-button text="Back" class="screen-buttons" @click="back" :color="color"/>
                <secondary-button 
                    :text="nextText" 
                    class="screen-buttons next-button" 
                    color="var(--white)" 
                    @click="next"
                    :icon="runIcon"
                />
            </div>
        </div>
    </div>
</template>

<script>
import obj from '@/util/obj'
import SecondaryButton from '../../../shared/components/buttons/SecondaryButton.vue'
import TestsSelector from '../../observe/inventory/components/TestsSelector.vue'
import UserConfig from './token/UserConfig.vue'
import TestRolesCondition from './test_roles/components/TestRolesCondition.vue'

export default{
    name: "TestingRunScreens",
    props:{
        item:obj.objR,
    },
    components:{
        SecondaryButton,
        TestsSelector,
        UserConfig,
        TestRolesCondition
    },
    data(){
        return{
            currIndex:1,
            summaryActive:false,
            stepperData:[
                'COLLECTION','TEST SUITES','USER CONFIG'
            ],
            andConditions:[],
            orConditions:[],
            total_collections:0,
            totalTestsSelected:0,
            selectedTestCategories:[],
            testsSelected:{},
            totalEndpoints:0,
            collectionName: "one_click_test",
            showTestScreen:true,
            showTestSummary:false,
            showCollectionSummary:false,
            selectedCollection:[],
            endPointsTaken:{},
        }
    },
    methods:{
        toggle(){
            this.showTestScreen = false
            this.reset()
        },
        back(){
            if(this.currIndex > 1){
                this.currIndex--
            }
        },
        reset(){
            this.currIndex = 1
            this.summaryActive = false
            this.andConditions = []
            this.orConditions = []
            this.selectedCollection = []
            this.endPointsTaken = {}
        },
        next(){
            this.currIndex++
            if(this.currIndex == 4){
                this.summaryActive = true
            }
            else if(this.currIndex == 5){
                this.runTest()
            }
            else{
                this.summaryActive = false
            }
        },
        runTest(){
            console.log("RunTest")
        },
        getTestsSelected(tests,categories){
            let _this = this
            _this.totalTestsSelected = 0
            _this.testsSelected = tests
            _this.selectedTestCategories=[]
            categories.forEach((x) => {
                if(tests[x.name] && tests[x.name].selected.length > 0){
                    _this.selectedTestCategories.push(x)
                    _this.totalTestsSelected += (_this.testsSelected[x.name].selected.length)
                }
            })   
        },
        getCollections(andConditions,orConditions){
            this.totalEndpoints = 0
            if(andConditions){
                this.andConditions = andConditions.predicates
            }
            if(orConditions){
                this.orConditions = orConditions.predicates
            }

            this.orConditions.forEach((item)=>{
                if(item.value){
                    this.totalEndpoints += (item.value.length)
                    let key = this.mapCollectionIdToName[item.value[0].apiCollectionId]
                    let value = item.value
                    this.selectedCollection.push(key)
                    Object.assign(this.endPointsTaken, {[key]: value});
                }
            })
            this.andConditions.forEach((item)=>{
                if(item.value){
                    this.totalEndpoints += (item.value.length)
                    let key = this.mapCollectionIdToName[item.value[0].apiCollectionId]
                    let value = item.value
                    this.selectedCollection.push(key)
                    Object.assign(this.endPointsTaken, {[key]: value});
                }
            })
            this.total_collections = this.andConditions.length + this.orConditions.length
        }
    },
    computed:{
        color(){
            if(this.currIndex > 1){return "var(--themeColorDark)"}       
            return "var(--lighten2)"
        },
        nextText(){
            if(this.currIndex > 3){return "Run Test"}       
            return "Next"
        },
        runIcon(){
            if(this.currIndex > 3){return "$fas_play"}      
        },
        openTests(){
            return this.selectedTestCategories
        },
        mapCollectionIdToName() {
            return this.$store.state.collections.apiCollections.reduce((m, e) => {
                m[e.id] = e.displayName
                return m
            }, {})
        }
    },
    watch:{
        item(newVal){
            this.showTestScreen=true
            this.reset()
        }
    }
}
</script>
<style scoped>
    .screen-buttons >>> .show-buttons{
        min-width: 72px !important;
        min-height: 34px !important;
    }
    .next-button >>> .show-buttons{
        background-color: var(--themeColorDark) !important;
    }
    .stepper-title >>> .v-stepper__step__step{
        border: 4px solid var(--borderColor) !important;
        background-color: var(--white) !important;
        order:2;
    }
    .v-stepper__step--complete >>> .v-stepper__step__step{
        background-color: var(--themeColor) !important;
    }
    .stepper-title >>> .primary{
        border-color: var(--themeColor) !important;
    }
    .tests-category-container>>>.v-list-group__header{
        height: 48px !important;
    }

    .tests-category-container>>>.v-list-group__header__prepend-icon{
        margin: auto !important;
    }
    .tests-category-container >>>.tests-category-container-active{
        background: var(--lighten2) !important;
        border: 1px solid !important;   
    }
    .tests-category-container >>>.v-list-group__items{
        padding: 4px 24px !important;
    }
</style>
<style lang="scss" scoped>
    ::-webkit-scrollbar {
        display: none !important;
    }
    .no-display{
        display: none !important;
    }
    .screens-container{
        display: flex;
        flex-direction: column;
        height: 100%;
        .screen-header{
            height: 80px;
            padding: 16px 32px;
            border-bottom: 1px solid var(--borderColor);
            display: flex;
            align-items: center;
            justify-content: space-between;

            span{
                display: flex;
                gap: 4px;
                align-items: center;
                height: 31px;
                font-weight: 500;
                font-size: 24px;
                color: var(--themeColorDark);
            }
        }
        .screen-body{
            margin: 20px 8px;
            padding:4px;
            .stepper,.stepper-header{
                box-shadow: none !important;
            }
            .divider{
                border: 2px solid var(--borderColor) !important;
                margin-top:27px;
            }
        }
        .screen-footer{
            position: absolute;
            bottom: 72px;
            width: 100%;
            border-top: 1px solid var(--borderColor);
            padding: 16px 32px 8px 32px;
        }
    }
    .collection-text{
        font-size: 18px;
        font-weight: 500;
        display: flex;
        color: var(--themeColorDark);
    }
    .selected-text{
        height: 48px;
        padding: 12px 16px;
        background: var(--hexColor31);
        border-radius: 8px;
        border: 1px solid var(--borderColor);
        span{
            font-size: 14px;
            color:var(--themeColorDark);
        }
        .bold-text{
            font-weight: 500 !important;
        }
    }
    .stepper-title{
        display: flex;
        flex-direction: column;
        gap: 12px
    }
    .summary-text{
        font-size: 20px;
        font-weight: 600;
        color: var(--themeColorDark);
        width: 102px;
        height: 26px;
        display: flex;
        margin-bottom: 8px;
    }
    .summary-container{
        display: flex;
        flex-direction: column;
        gap: 16px;
        padding: 8px 0px;
        .summary-boxes{
            height: 110px;
            width: 100%;
            border: 2px solid var(--borderColor);
            border-radius: 12px;
            padding: 24px 24px 4px 24px;
            display: flex;
            gap:12px;
            .summary-body{
                display: flex;
                flex-direction: column;
                gap: 8px;
                width: 100%;
                .summary-header{
                    display: flex;
                    justify-content: space-between;
                    align-items: center;
                    .header-title{
                        font-size: 20px;
                        color: var(--themeColorDark);
                        font-weight: 600;
                    }
                }
                .button-containers{
                    gap:8px;
                    display: flex;
                    .test-categories{
                        height: 28px;
                        min-width: 50px;
                        background: var(--hexColor39);
                        padding: 2px 8px;
                        border-radius: 4px;
                        display: flex;
                        justify-content: center;
                        
                        span{
                            font-size: 16px;
                            color: var(--black);
                            font-weight: 500;
                        }
                    }
                }
                .summary-collection-text {
                    font-size: 16px;
                    font-weight: 500;
                    color: var(--themeColorDark);
                }
            }
        }
    }
    .summary-list{
        max-height: 250px;
        border-width: 0px 1px 1px 1px;
        border-style: solid;
        border-color: var(--borderColor);
        border-radius: 0px 0px 12px 12px;
        padding: 6px;

        .test-list-container{
            max-height: 235px;
            overflow-y: scroll;
            display: flex;
            flex-direction: column;
            gap: 5px;
            align-items: center;
            padding: 8px 12px ;
            .tests-category-container{
                width: 1150px;
                .test-container{
                    color:var(--themeColorDark) !important;
                }
                .test-name{
                    font-size: 12px !important;
                }
            }
        }
    }
</style>