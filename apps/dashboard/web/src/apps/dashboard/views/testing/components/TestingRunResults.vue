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
            <div class="testing-results-header">
                <span>Test results: </span>    
                <span>{{selectedDateStr()}}</span>
                <span
                    v-for="(data, index) in testResultsChartData()"
                    :key="'chip_'+index"            
                >
                    <v-chip
                        :color="data.color.substr(0, 7)+'30'"
                        class="mx-2"
                        small
                    >
                        <span :style="{'color': data.color.substr(0, 7)+'ff'}">{{data.data}} {{selectedDate}}{{data.name.toLowerCase()}}</span>
                    </v-chip>
                </span>
                
            </div>  
        </div>
    </div>
</template>

<script>

import obj from "@/util/obj"
import func from "@/util/func"
import DateRange from '@/apps/dashboard/shared/components/DateRange'
import ACard from '@/apps/dashboard/shared/components/ACard'
import StackedChart from '@/apps/dashboard/shared/components/charts/StackedChart'

export default {
    name: "TestingRunResults",
    props: {
        testId: obj.numR,
        defaultStartTimestamp: obj.numN,
        defaultEndTimestamp: obj.numN
    },
    components: {
        DateRange,
        ACard,
        StackedChart
    },
    data () {
        return {
            title: "Unauthenticated",
            endpoints: 100,
            isDaily: true,
            testTypes: ["Bola", "Workflow", "Bua"],
            testingRuns: [],
            startTimestamp: this.defaultStartTimestamp || (func.timeNow() - func.recencyPeriod/9),
            endTimestamp: this.defaultEndTimestamp || func.timeNow(),
            selectedDate: +func.dayStart(Math.min(...this.testingRuns.map(x => x.timestamp))*1000)
        }
    },
    methods: {
        selectedDateStr() {
            return func.toTimeStr(new Date(this.selectedDate), true)
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
        }
    },
    computed: {
        dateRange: {
            get () {
                return [this.toHyphenatedDate(this.startTimestamp * 1000), this.toHyphenatedDate(this.endTimestamp * 1000)]
            },
            set(newDateRange) {
                this.startTimestamp = parseInt(this.toEpochInMs(newDateRange[0]) / 1000)
                this.endTimestamp = parseInt(this.toEpochInMs(newDateRange[1]) / 1000)
                this.selectedDate = this.endTimestamp*1000
            }
        },
        currentTest() {
            return this.testingRuns.filter(x => +func.dayStart(x.timestamp*1000) === this.selectedDate)
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