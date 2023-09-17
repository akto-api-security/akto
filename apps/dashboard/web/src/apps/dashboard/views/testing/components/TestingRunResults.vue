<template>
    <div class="testing-run-results-container" ref="detailsDialog">
        <div class="testing-run-header">
            <span class="testing-run-title">{{(testingRun && testingRun.name) || "Tests"}}</span>
            <span>({{endpoints}})</span> | 
            <span>{{getScheduleStr()}}</span> | 
            <span>{{collectionName}}</span>
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
                    <date-range v-model="dateRange"/>
                </div>
                <stacked-chart
                    type='column'
                    color='var(--hexColor33)'
                    :areaFillHex="false"
                    :height="250"
                    title="Test results"
                    :data="testResultsChartData()"
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
                            :sensitiveTags="item.severity ? [item.severity] : []" 
                            :chipColor="getColor(item.severity)"
                            :hideTag="true"
                            class="z-80"
                        />
                    </template>
                
                </simple-table>
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
            refreshTestResultsInterval : null
        }
    },
    methods: {
        getColor(severity) {
            switch (severity) {
                case "HIGH": return "var(--hexColor33)"
                case "MEDIUM":  return "var(--hexColor34)"
                case "LOW": return "var(--hexColor35)"
            }
            
        },
        selectedDateStr() {
            return func.toTimeStr(new Date(this.currentTest.startTimestamp * 1000), true)
        },
        getScheduleStr() {
            return this.isDaily ? "Running daily" : "Run once"
        },
        toHyphenatedDate(epochInMs) {
            return func.toDateStrShort(new Date(epochInMs))
        },
        testResultsChartData () {
            let retH = []
            let retM = []
            let retL = []

            this.testingRunResultSummaries.forEach((x) => {
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
                this.selectedDate = Math.max(...this.testingRunResultSummaries.map(o => o.startTimestamp))
            })
        },
        prepareForTable(runResult) {
            return {
                ...runResult,
                endpoint: runResult.apiInfoKey.method + " " + runResult.apiInfoKey.url,
                severity: runResult["vulnerable"] ? func.getRunResultSeverity(runResult, this.subCatogoryMap) : null,
                testSubType: func.getRunResultSubCategory (runResult, this.subCategoryFromSourceConfigMap, this.subCatogoryMap, "testName"),
                testSuperType: func.getRunResultCategory(runResult, this.subCatogoryMap, this.subCategoryFromSourceConfigMap, "shortName")
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

        if (this.testingRunResultSummaries.length !== 0) {
            this.loading = false
        } else {
            this.refreshSummariesInterval = setInterval(() => {
                this.refreshSummaries().then(() => {
                if (this.testingRunResultSummaries.length !== 0) {
                    this.loading = false
                    clearInterval(this.refreshSummariesInterval)
                }
                })
            }, 5000)
        }

        this.refreshTestResultsInterval = setInterval(() => {
            if (this.currentTest && (this.currentTest.state === "SCHEDULED" || this.currentTest.state === "RUNNING")) {
                this.refreshSummaries()
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
                api.fetchTestingRunResults(currentSummary.hexId).then(resp => {
                    this.testingRunResults = resp.testingRunResults
                })
            }
            return currentSummary
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