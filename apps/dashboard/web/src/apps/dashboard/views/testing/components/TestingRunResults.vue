<template>
    <div class="testing-run-results-container">
        <div class="testing-run-header">
            <span class="testing-run-title">{{title}}</span>
            <span>({{endpoints}})</span> | 
            <span>{{getScheduleStr()}}</span> | 
            <span
                v-for="(testType, index) in testTypes"
                :key="'chip_'+index"            
            >
                <v-chip
                    color="#6200EA19"
                    class="mx-2"
                    small
                >
                    <span class="primary--text">{{testType.toLowerCase()}}</span>
                </v-chip>
                <span v-if="index != testTypes.length - 1"  class="primary--text">+ </span>
            </span>
        </div>

        <div class="testing-runs-history">
            <div class="d-flex jc-end">
                <date-range v-model="dateRange"/>
            </div>
            <stacked-chart
                type='column'
                color='#FF000080'
                :areaFillHex="false"
                :height="250"
                title="Test results"
                :data="testResultsChartData()"
                :defaultChartOptions="{legend:{enabled: false}}"
                background-color="rgba(0,0,0,0.0)"
                :text="true"
                class="pa-5"
                @dateClicked=dateClicked
            />     
            <div class="testing-results-header" v-if="currentTest">
                <span>Test results: </span>    
                <span>{{selectedDateStr()}}</span>
            </div>                  
            <simple-table
                :headers="testingRunResultsHeaders" 
                :items="testingRunResultsItems" 
                name="" 
                sortKeyDefault="isVulnerable" 
                :sortDescDefault="true"
                @rowClicked="openDetails"
            >
                <template #item.severity="{item}">
                    <sensitive-chip-group 
                        :sensitiveTags="Array.from([item.severity] || new Set())" 
                        :chipColor="getColor(item.severity)"
                        :hideTag="true"
                    />
                </template>
            
            </simple-table>

            <v-dialog v-model="openDetailsDialog">
                <div class="details-dialog">
                    <a-card
                        title="Test details"
                        color="rgba(33, 150, 243)"
                        subtitle=""
                        icon="$fas_stethoscope"
                    >
                        <template #title-bar>
                            <v-btn
                                plain
                                icon
                                @click="openDetailsDialog = false"
                                style="margin-left: auto"
                            >
                                <v-icon>$fas_times</v-icon>
                            </v-btn>
                        </template>
                        <div class="pa-4">
                            <test-results-dialog :testingRunResult="testingRunResult"/>
                        </div>
                    </a-card>
                </div>
            </v-dialog>
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

import api from '../api'

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
        TestResultsDialog
    },
    data () {
        let endTimestamp = this.defaultEndTimestamp || func.timeNow()
        return {
            title: "Unauthenticated",
            testTypes: ["Bola", "Workflow", "Bua"],
            startTimestamp: this.defaultStartTimestamp || (func.timeNow() - func.recencyPeriod/9),
            endTimestamp: endTimestamp,
            selectedDate: +func.dayStart(endTimestamp * 1000),
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
                }
            ],
            testingRunResult: null,
            openDetailsDialog: false
        }
    },
    methods: {
        getColor(severity) {
            switch (severity) {
                case "HIGH": return "#FF000080"
                case "MEDIUM":  return "#FF5C0080"
                case "LOW": return "#F9B30080"
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
            let todayDate = new Date(this.endTimestamp * 1000)
            let twoMonthsAgo = new Date(this.startTimestamp * 1000)
            
            let currDate = twoMonthsAgo
            let ret = []
            while (currDate <= todayDate) {
                ret.push([func.toDate(func.toYMD(currDate)), parseInt(Math.random()*100) || 0])
                currDate = func.incrDays(currDate, 1)
            }

            return [
                {
                    data: ret,
                    color: "#FF000080",
                    name: "High"
                },
                {
                    data: ret,
                    color: "#FF5C0080",
                    name: "Medium"
                },
                {
                    data: ret,
                    color: "#F9B30080",
                    name: "Low"
                }
            ]
        },
        dateClicked(point) {
            this.selectedDate = point
            console.log(this.selectedDate)
        },
        refreshSummaries() {
            api.fetchTestingRunResultSummaries(this.startTimestamp, this.endTimestamp, this.testingRunHexId).then(resp => {
                this.testingRunResultSummaries = resp.testingRunResultSummaries
            })
        },
        prepareForTable(runResult) {
            return {
                ...runResult,
                endpoint: runResult.apiInfoKey.method + " " + runResult.apiInfoKey.url,
                severity: (runResult.testResults || []).reduce((z, e) => {
                    if (z === "HIGH" || e === "HIGH") return z

                    if (z === "MEDIUM" || e === "MEDIUM") return "MEDIUM"

                    return "LOW"
                }, "LOW")
            }
        },
        async openDetails(row) {
            api.fetchTestRunResultDetails(row["hexId"]).then(resp => {
                this.testingRunResult = resp["testingRunResult"]
                if (this.testingRunResult) {
                    this.openDetailsDialog = true
                }
            })
        },
    },
    mounted() {
        this.refreshSummaries()
    },
    computed: {
        ...mapState('testing', ['testingRuns', 'pastTestingRuns']),
        testingRun() {
            return [...this.testingRuns, ...this.pastTestingRuns].filter(x => x.hexId === this.testingRunHexId)[0]
        },
        endpoints() {
            return this.testingRun ? testing.getEndpoints(this.testingRun.testingEndpoints) : "-"
        },
        dateRange: {
            get () {
                return [this.toHyphenatedDate(this.startTimestamp * 1000), this.toHyphenatedDate(this.endTimestamp * 1000)]
            },
            set(newDateRange) {
                this.startTimestamp = parseInt(this.toEpochInMs(newDateRange[0]) / 1000)
                this.endTimestamp = parseInt(this.toEpochInMs(newDateRange[1]) / 1000)
                this.selectedDate = this.endTimestamp*1000
                this.refreshSummaries()
            }
        },
        currentTest() {
            let currentSummary = this.testingRunResultSummaries.filter(x => +func.dayStart(x.startTimestamp*1000) === this.selectedDate)[0]
            if (currentSummary) {
                api.fetchTestingRunResults(currentSummary.hexId).then(resp => {
                    this.testingRunResults = resp.testingRunResults
                })
            }
            return currentSummary
        },
        testingRunResultsItems() {
            return (this.testingRunResults || []).map(x => this.prepareForTable(x))
        }
    }
}
</script>

<style lang="sass" scoped>
.testing-run-results-container
    color: #47466A !important
    
.testing-run-title
    font-weight: 500 

.testing-run-header       
    font-size: 14px

.testing-runs-history
    padding: 16px    

.testing-results-header
    font-size: 14px        
    font-weight: 500
    color: #47466A80
</style>