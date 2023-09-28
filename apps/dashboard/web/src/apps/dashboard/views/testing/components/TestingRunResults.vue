<template>
    <div class="testing-run-results-container" ref="detailsDialog">
        <div class="d-flex justify-space-between">
            <div class="testing-run-header pt-2" style="display: flex; flex-direction: column;">
                <v-tooltip bottom>
                    <template v-slot:activator='{ on, attrs }'>
                        <span v-bind="attrs" v-on="on" class="testing-run-title">{{(testingRun && testingRun.name) || "Tests"}}</span>
                    </template>
                    <span>{{(testingRun && testingRun.name) || "Tests"}}x</span>
                </v-tooltip>
                <div>
                    <span>({{endpoints}})</span> |
                    <span>{{getScheduleStr()}}</span> |
                    <span>{{collectionName}}</span>
                </div>
            </div>
            <div  v-if="this.currentTest.state === 'COMPLETED'" class="mr-5">
                <v-tooltip bottom>
                    <template v-slot:activator='{on, attrs}'>
                        <v-btn
                            icon
                            color="var(--themeColorDark)"
                            @click="rerunTest"
                            v-on="on"
                            v-bind="attrs"
                        >
                            <v-icon>$fas_redo</v-icon>
                        </v-btn>
                    </template>
                    Rerun test
                </v-tooltip>
            </div>
        </div>

        <div class="loading-bar" v-if="loading">
            <div>
                <spinner :size="50" color="var(--themeColor)"/>
            </div>
            <div style="padding-top: 12px;">
                <div class="joke-line" v-for="line in jokes[jokeIndex]">
                    {{ line }}
                </div>
            </div>
        </div>

        <div v-else>
            <div class="testing-runs-history" v-if="!isWorkflow">
                <div class="d-flex jc-end">
                <div  v-if=" runType==='cicd' " v-for = "(header,index) in this.metadataFilterData">
                    <v-menu :key="index" offset-y :close-on-content-click="false"> 
                    <template v-slot:activator="{ on, attrs }">
                            <secondary-button 
                            :text="header.text" 
                            v-bind="attrs"
                            v-on="on"
                            :color="metadataFilters[header.value].size > 0 ? 'var(--themeColor) !important' : null">
                            </secondary-button>
                        </template>
                        <filter-column
                            :title="header.text"
                            :typeAndItems="header.data"
                            @clickedItem="appliedFilter(header.value, $event)" 
                            @operatorChanged="operatorChanged(header.value, $event)"
                            @selectedAll="selectedAll(header.value, $event)"
                            :listOperators="['OR', 'NOT']"
                        />
                    </v-menu>
                </div>
                    <date-range v-model="dateRange"/>
                </div>
                <stacked-chart
                    type='column'
                    color='var(--hexColor33)'
                    :areaFillHex="false"
                    :height="250"
                    title="Test results"
                    :data="testResultsChartData()"
                    :tooltipMetadata="getChartTooltipMetadata()"
                    :defaultChartOptions="{legend:{enabled: false}}"
                    background-color="var(--transparent)"
                    :text="true"
                    class="pa-5"
                    @dateClicked=dateClicked
                />     
                <div class="testing-results-header" v-if="currentTest">
                    <div>
                        <span>Test results: </span>    
                        <span>{{selectedDateStr()}}</span>
                    </div>
                    <div v-if="this.currentTest.state === 'COMPLETED'">
                        <span>Time taken: </span>
                        <span>{{getTimeTakenByTest()}}</span>
                    </div>
                    <div style="display: flex; text-transform: capitalize;">
                        <div v-if="this.currentTest.state">Test status: {{this.currentTest.state.toLowerCase()}}</div>
                        <div v-if="this.currentTest.state === 'SCHEDULED' || this.currentTest.state === 'RUNNING' " style="padding-left: 6px; padding-top: 4px;">
                            <v-progress-circular indeterminate color="primary" :size="12" :width="1.5"></v-progress-circular>
                        </div>
                        <span v-if="this.currentTest.state === 'COMPLETED'" style="padding-top: 4px;">
                            <v-icon color="green" :size="14">$fas_check-circle</v-icon>
                        </span>
                    </div>
                </div>                  

                <div class="testing-results-header" v-if="this.currentTest && this.currentTest.metadata">
                    CI/CD Run Details: {{ this.currentTest.metadata }}
                </div>

                <layout-with-tabs title="" :tabs="['Vulnerable', 'All']">
                <template slot="Vulnerable">
                    <simple-table
                    :headers="testingRunResultsHeaders" 
                    :items="vulnerableTestingRunResultsItems" 
                    name="" 
                    sortKeyDefault="vulnerable" 
                    :pageSize="10"
                    :sortDescDefault="true"
                    :dense="true"
                    @rowClicked="openDetails"
                    @exportAsHTML="exportAsHTML"
                    :showExportVulnerabilityButton="true"
                >
                    <template #item.severity="{item}">
                        <sensitive-chip-group 
                            :sensitiveTags="(item.severity || item.severity.value !== 0) ? getItemSeverity(item.severity.value) : []"
                            :chipColor="getColor(item.severity.value)"
                            :hideTag="true"
                            class="z-80"
                        />
                    </template>
                
                    <template #item.cwe="{item}">
                        <sensitive-chip-group 
                            :sensitiveTags="item.cwe"
                            :chipColor="getColor(2)"
                            :hideTag="true"
                            class="z-80"
                        />
                    </template>
                    </simple-table>
                </template>
                <template slot="All">
                    <simple-table
                    :headers="testingRunResultsHeaders" 
                    :items="testingRunResultsItems" 
                    name="" 
                    sortKeyDefault="vulnerable" 
                    :pageSize="10"
                    :sortDescDefault="true"
                    :dense="true"
                    @rowClicked="openDetails"
                    >
                    <template #item.severity="{item}">
                        <sensitive-chip-group 
                            :sensitiveTags="(item.severity || item.severity.value !== 0) ? getItemSeverity(item.severity.value) : []"
                            :chipColor="getColor(item.severity.value)"
                            :hideTag="true"
                            class="z-80"
                        />
                    </template>

                    <template #item.cwe="{item}">
                        <sensitive-chip-group 
                            :sensitiveTags="item.cwe"
                            :chipColor="getColor(2)"
                            :hideTag="true"
                            class="z-80"
                        />
                    </template>

                </simple-table>
                </template>
                </layout-with-tabs>
                <div v-if="openDetailsDialog">
                    <div class="details-dialog z-80">
                        <test-results-dialog 
                            :similarlyAffectedIssues="similarlyAffectedIssues"
                            :testingRunResult="testingRunResult"
                            :subCatogoryMap="subCatogoryMap"
                            :issuesDetails="dialogBoxIssue"
                            isTestingPage
                            :mapCollectionIdToName="mapCollectionIdToName"/>
                    </div>
                </div>
                
            </div>
            <div v-else>
                <workflow-test-builder :endpointsList="[]" apiCollectionId=0 :originalStateFromDb="originalStateFromDb" :defaultOpenResult="true" class="white-background"/>
            </div>
        </div>
    </div>
</template>

<script>
import DateRange from '@/apps/dashboard/shared/components/DateRange'
import ACard from '@/apps/dashboard/shared/components/ACard'
import StackedChart from '@/apps/dashboard/shared/components/charts/StackedChart'
import SimpleTable from '@/apps/dashboard/shared/components/SimpleTable'
import SensitiveChipGroup from '@/apps/dashboard/shared/components/SensitiveChipGroup'
import TestResultsDialog from "./TestResultsDialog";
import WorkflowTestBuilder from '../../observe/inventory/components/WorkflowTestBuilder'
import Spinner from '@/apps/dashboard/shared/components/Spinner'
import FilterColumn from '../../../shared/components/FilterColumn'
import SecondaryButton from '../../../shared/components/buttons/SecondaryButton'
import LayoutWithTabs from '@/apps/dashboard/layouts/LayoutWithTabs'

import api from '../api'
import issuesApi from '../../issues/api'

import obj from "@/util/obj"
import func from "@/util/func"
import testing from "@/util/testing"

import {mapState} from "vuex"

export default {
    name: "TestingRunResults",
    props: {
        testingRunHexId: obj.strR,
        defaultStartTimestamp: obj.numN,
        defaultEndTimestamp: obj.numN
    },
    components: {
        DateRange,
        ACard,
        StackedChart,
        SimpleTable,
        SensitiveChipGroup,
        TestResultsDialog,
        WorkflowTestBuilder,
        Spinner,
        FilterColumn,
        SecondaryButton,
        LayoutWithTabs,
        Spinner
    },
    data () {
        let endTimestamp = this.defaultEndTimestamp || func.timeNow()
        return {
            sticky: false,
            title: "Test",
            testTypes: ["Bola", "Workflow", "Bua"],
            startTimestamp: this.defaultStartTimestamp || (func.timeNow() - func.recencyPeriod/9),
            endTimestamp: endTimestamp,
            selectedDate: +func.dayStart(endTimestamp * 1000) / 1000,
            testingRunResultSummaries: [],
            testingRunResults: [],
            vulnerableTestingRunResults: [],
            testingRunResultsHeaders: [
                {
                    text: "",
                    value: "color"
                },
                {
                    text: "Endpoint",
                    value: "endpoint"
                },
                {
                    text: "Issue category",
                    value: "testSuperType"
                },
                {
                    text: "Test",
                    value: "testSubType"
                },
                {
                    text: "Severity",
                    value: "severity"
                },
                {
                    text: "Vulnerable",
                    value: "vulnerable"
                },
                {
                    text: "CWE",
                    value: "cwe"
                }
            ],
            testingRunResult: null,
            openDetailsDialog: false,
            isWorkflow: false,
            originalStateFromDb: null,
            dialogBoxIssue: {},
            similarlyAffectedIssues: [],
            loading: true,
            jokes: [
                ["Looking for love in all the wrong places", "../../etc/pwd"],
                ["May the API security be with you"],
                ["Unsecured API: I will look for you, I will find you, and I will kill you"],
                ["Why waste time use lot tool when few tool do trick", "Akto API Security"],
                ["You can't handle the truth", "?id=1 OR 2=2"],
                ["Break Bugs Not Hearts"],
                ["Houston, we have API security!"],
                ["Your mission: API security, should you choose to accept it?"],
                ["API Security matters!!!"]
            ],
            refreshSummariesInterval: null,
            refreshTestResultsInterval : null,
            metadataFilters:  {
                branch: new Set(),
                repository: new Set()
            },
            metadataFilterOperators: {
                branch: "OR",
                repository: "OR"
            },
            metadataFilterData: [],
            runType: "oneTime"
        }
    },
    methods: {
        async exportAsHTML() {
            let currentSummary = this.testingRunResultSummaries.filter(x => x.startTimestamp === this.selectedDate)[0]
            const routeData = this.$router.resolve({name: 'testing-export-html', query: {testingRunResultSummaryHexId:currentSummary.hexId}});
            window.open(routeData.href, '_blank');
        },
        getColor(severity) {
            switch (severity) {
                case 3: return "var(--hexColor33)"
                case 2:  return "var(--hexColor34)"
                case 1: return "var(--hexColor35)"
            }  
        },
        getItemSeverity(severity){
            switch (severity) {
                case 3: return ["HIGH"]
                case 2:  return ["MEDIUM"]
                case 1: return ["LOW"]
                default: return []
            }
        },
        selectedDateStr() {
            return func.toTimeStr(new Date(this.currentTest.startTimestamp * 1000), true)
        },
        getTimeTakenByTest(){
            const timeDiff = Math.abs(this.currentTest.endTimestamp - this.currentTest.startTimestamp);
            const hours = Math.floor(timeDiff / 3600);
            const minutes = Math.floor((timeDiff % 3600) / 60);
            const seconds = timeDiff % 60;

            let duration = '';
            if (hours > 0) {
                duration += hours + 'h ';
            }
            if (minutes > 0) {
                duration += minutes + 'm ';
            }
            if (seconds > 0 || (hours === 0 && minutes === 0)) {
                duration += seconds + 's';
            }
            return duration.trim();
        },
        async rerunTest(){
            await this.$store.dispatch('testing/rerunTest', {testingRunHexId: this.testingRunHexId})
        },
        getScheduleStr() {

            switch(this.runType){
                case "cicd":
                    return "CI/CD"
                case "recurring":
                    return "Running daily"
                default:
                    return "Run once"
            }
        },
        toHyphenatedDate(epochInMs) {
            return func.toDateStrShort(new Date(epochInMs))
        },
        testResultsChartData () {
            let retH = []
            let retM = []
            let retL = []

            let items = this.testingRunResultSummaries;

            items = items.filter((x) => {
                let ret = true;
                Object.keys(this.metadataFilters).forEach((key) => {
                    if(this.metadataFilters[key].size > 0){
                        switch(this.metadataFilterOperators[key]){
                            case "AND":
                            case "OR":
                                ret &= (this.metadataFilters[key].has(x?.metadata?.[key]))
                                break;
                            case "NOT":
                                ret &= !(this.metadataFilters[key].has(x?.metadata?.[key]))
                        }
                    }
                })

                return ret
            })

            items.forEach((x) => {
                let ts = x["startTimestamp"] * 1000
                let countIssuesMap = x["countIssues"]

                let dt = +func.dayStart(ts)
                let s = +func.dayStart(this.startTimestamp*1000)
                let e = +func.dayStart(this.endTimestamp*1000)
                if (dt < s || dt > e) return

                retH.push([ts, countIssuesMap["HIGH"]])
                retM.push([ts, countIssuesMap["MEDIUM"]])
                retL.push([ts, countIssuesMap["LOW"]])
            })

            return [
                {
                    data: retH,
                    color: "var(--hexColor33)",
                    name: "High"
                },
                {
                    data: retM,
                    color: "var(--hexColor34)",
                    name: "Medium"
                },
                {
                    data: retL,
                    color: "var(--hexColor35)",
                    name: "Low"
                }
            ]
        },
        getChartTooltipMetadata(){

            let ret = {}

            this.testingRunResultSummaries.forEach((x) => {
                let ts = x["startTimestamp"] * 1000

                let dt = +func.dayStart(ts)
                let s = +func.dayStart(this.startTimestamp*1000)
                let e = +func.dayStart(this.endTimestamp*1000)
                if (dt < s || dt > e) return

                ret[ts] = {
                    branch: x?.metadata?.branch,
                    repository: x?.metadata?.repository
                }
            })

            return ret;
        },
        async processMetadataFilters(){

            let ret = []
            let tmp = {
                branch: [],
                repository: []
            }

            let res = await api.fetchMetadataFilters()
            tmp = res.metadataFilters

            Object.keys(tmp).forEach((key) => {
                if(tmp[key].length > 0){
                    ret.push({
                        text: func.toSentenceCase(key),
                        value: key,
                        data: {
                            type: "STRING",
                            values: [...tmp[key]].map((x) => {
                                return {
                                    title: x,
                                    subtitle: '',
                                    value: x
                                }
                            })
                        }
                    })
                }
            })

            this.metadataFilterData = ret;
        },
        selectedAll (hValue, {items, checked}) {
            for(var index in items) {
                if (checked) {
                    this.metadataFilters[hValue].add(items[index].value)
                } else {
                    this.metadataFilters[hValue].delete(items[index].value)
                }
            }
            this.metadataFilters = {...this.metadataFilters}
        },
        appliedFilter (hValue, {item, checked, operator}) { 

            this.metadataFilterOperators[hValue] = operator || 'OR'
            if (checked) {
                this.metadataFilters[hValue].add(item.value)
            } else {
                this.metadataFilters[hValue].delete(item.value)
            }
            this.metadataFilters = {...this.metadataFilters}
        },
        operatorChanged(hValue, {operator}) {
            this.metadataFilterOperators[hValue] = operator || 'OR'
        },
        dateClicked(point) {
            this.selectedDate = point / 1000
        },
        refreshSummaries(firstTime) {

            let st = this.startTimestamp
            let en = this.endTimestamp

            if(firstTime){
                st = 0;
                en = 0;
            }

            return api.fetchTestingRunResultSummaries(st, en, this.testingRunHexId).then(resp => {
                if (resp.testingRun.testIdConfig == 1) {
                    this.isWorkflow = true
                    this.originalStateFromDb = resp.workflowTest
                }
                this.testingRunResultSummaries = resp.testingRunResultSummaries
                if(resp.testingRun?.scheduleTimestamp > func.timeNow()){
                    this.runType="recurring"
                }
                
                this.testingRunResultSummaries.forEach((x) => {
                    if(x.metadata){
                        this.runType="cicd"
                    }
                })
                this.selectedDate = Math.max(...this.testingRunResultSummaries.map(o => o.startTimestamp))
            })
        },
        prepareForTable(runResult) {
            return {
                ...runResult,
                endpoint: runResult.apiInfoKey.method + " " + runResult.apiInfoKey.url,
                severity: runResult["vulnerable"] ? func.getRunResultSeverity(runResult, this.subCatogoryMap) : {title: "NONE", value: 0},
                testSubType: func.getRunResultSubCategory (runResult, this.subCategoryFromSourceConfigMap, this.subCatogoryMap, "testName"),
                testSuperType: func.getRunResultCategory(runResult, this.subCatogoryMap, this.subCategoryFromSourceConfigMap, "shortName"),
                cwe: func.getRunResultCwe(runResult, this.subCatogoryMap)
            }
        },
        async openDetails(row) {
            let _this = this
            await api.fetchTestRunResultDetails(row["hexId"]).then(async resp => {
                _this.testingRunResult = resp["testingRunResult"]
                if (_this.testingRunResult) {
                    await api.fetchIssueFromTestRunResultDetails(row["hexId"]).then(async respIssue => {
                        _this.dialogBoxIssue = respIssue['runIssues']
                        if (_this.dialogBoxIssue) {
                            await issuesApi.fetchAffectedEndpoints(_this.dialogBoxIssue.id).then(affectedResp => {
                                _this.similarlyAffectedIssues = affectedResp['similarlyAffectedIssues']
                            })
                        }
                    })
                    _this.openDetailsDialog = true
                }
            })
            if (!_this.sticky) {
                let detailsDialogEl = _this.$refs['detailsDialog']
                detailsDialogEl.scrollIntoView({block: "end", inline: "nearest", behavior: 'smooth'})
            } else {
                _this.sticky = true                        
            }
        },
    },
    async mounted() {
        await this.$store.dispatch('issues/fetchAllSubCategories')
        await this.refreshSummaries(true)
        await this.processMetadataFilters()

        if (this.testingRunResultSummaries.length !== 0) {
            this.loading = false
        } else {
            this.refreshSummariesInterval = setInterval(() => {
                this.refreshSummaries(true).then(() => {
                if (this.testingRunResultSummaries.length !== 0) {
                    this.loading = false
                    clearInterval(this.refreshSummariesInterval)
                }
                })
            }, 5000)
        }

        this.refreshTestResultsInterval = setInterval(() => {
            if (this.currentTest && (this.currentTest.state === "SCHEDULED" || this.currentTest.state === "RUNNING")) {
                this.refreshSummaries(true)
            }
        }, 5000)

        if(this.currentTest){
            if(this.startTimestamp > this.currentTest.startTimestamp){
                this.startTimestamp = this.currentTest.startTimestamp
            }
        }
    },

    destroyed() {
        clearInterval(this.refreshTestResultsInterval)
        clearInterval(this.refreshSummariesInterval)
    },

    computed: {
        ...mapState('testing', ['testingRuns', 'pastTestingRuns','cicdTestingRuns']),
        subCatogoryMap: {
            get() {
                return this.$store.state.issues.subCatogoryMap
            }
        },
        jokeIndex() {
            let min = 0
            let max = this.jokes.length - 1
            return Math.floor(Math.random() * (max - min + 1)) + min;
        },
        subCategoryFromSourceConfigMap: {
            get() {
                return this.$store.state.issues.subCategoryFromSourceConfigMap
            }
        },
        mapCollectionIdToName() {
            return this.$store.state.collections.apiCollections.reduce((m, e) => {
                m[e.id] = e.displayName
                return m
            }, {})
        },
        testingRun() {
            return [...this.testingRuns, ...this.pastTestingRuns, ...this.cicdTestingRuns].filter(x => x.hexId === this.testingRunHexId)[0]
        },
        endpoints() {
            return this.testingRun ? testing.getEndpoints(this.testingRun.testingEndpoints) : "-"
        },
        collectionName() {
            if (this.testingRun) {
                return testing.getCollectionName(this.testingRun.testingEndpoints, this.mapCollectionIdToName)
            } else {
                return ""
            }
        },
        dateRange: {
            get () {
                return [this.toHyphenatedDate(this.startTimestamp * 1000), this.toHyphenatedDate(this.endTimestamp * 1000)]
            },
            set(newDateRange) {
                let start = Math.min(func.toEpochInMs(newDateRange[0]), func.toEpochInMs(newDateRange[1]));
                let end = Math.max(func.toEpochInMs(newDateRange[0]), func.toEpochInMs(newDateRange[1]));

                this.startTimestamp = +func.dayStart(start) / 1000
                this.endTimestamp = +func.dayEnd(end) / 1000
                this.selectedDate = this.endTimestamp
                this.refreshSummaries()
            }
        },
        currentTest() {
            let currentSummary = this.testingRunResultSummaries.filter(x => x.startTimestamp === this.selectedDate)[0]
            if (currentSummary) {
                api.fetchTestingRunResults(currentSummary.hexId, true).then(resp => {
                    this.vulnerableTestingRunResults = resp.testingRunResults
                })
                api.fetchTestingRunResults(currentSummary.hexId).then(resp => {
                    this.testingRunResults = resp.testingRunResults
                })
            }
            return currentSummary
        },
        vulnerableTestingRunResultsItems(){
            let result = (this.vulnerableTestingRunResults || []).map(x => this.prepareForTable(x))
            return result.filter(x => x.vulnerable && x.testSubType && x.testSuperType)
        },
        testingRunResultsItems() {
            let result = (this.testingRunResults || []).map(x => this.prepareForTable(x))
            return result.filter(x => x.testSubType && x.testSuperType)
        }
    }
}
</script>

<style lang="sass" scoped>
.testing-run-results-container
    color: var(--themeColorDark) !important
    
.testing-run-title
    font-weight: 500 
    max-width: 650px
    text-overflow: ellipsis
    overflow : hidden
    white-space: nowrap

.testing-run-header       
    font-size: 14px

.testing-runs-history
    padding: 16px    

.testing-results-header
    font-size: 14px        
    font-weight: 500
    color: var(--themeColorDark9)
    margin-bottom:10px
    display: flex
    justify-content: space-between
    padding-right: 24px
.loading-bar
    display: flex
    justify-content: center
    height: 500px
    align-items: center
    flex-direction: column

.joke-line
    padding-top: 12px
    align-items: center
    display: flex
    justify-content: center
    color: var(--themeColor)
    font-weight: 500
    font-size: 16px
    
</style>
<style lang="scss" scoped>

.details-dialog{
    align-items:center;
    justify-content:center;
    max-height: 500px !important;
    min-height: 500px !important;
    height: 500px !important;
    overflow: scroll;
    width: 1200px !important;
    background: var(--white);
}

</style>