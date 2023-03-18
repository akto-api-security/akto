<template>
    <div class="pa-5 testing-summary-div">
        <div v-if="loading && this.testResults && this.testResults.length > 0" class="spinner-div">
            <spinner :size="50"/>
        </div>
        <v-tabs
            v-else
            v-model="tab"
            background-color="transparent"
            color="basil"
            grow
            class="tabs"
            :hide-slider="true"
        >
            <v-tab
                v-for="(item,idx) in this.items"
                :key="item"
                active-class="tab-selected"
            >
                {{ item }}
                <div :class="idx === tab ? 'count-chip count-chip-selected' : 'count-chip'">
                    {{ idx }}
                </div>
            </v-tab>
            <v-tabs-items v-model="tab">
                <div style="padding-top: 26px; ">
                    <div v-for="testResult in this.testResults" style="padding: 6px 0px 6px 0px">
                        <testing-result-card
                            :method= "testResult['method']"
                            :path= "testResult['path']"
                            :severity= "testResult['severity']"
                            :vulnerability= "testResult['vulnerability']"
                            :testName= "testResult['testName']"
                        />
                    </div>
                </div>
            </v-tabs-items>
        </v-tabs>
    </div>
</template>


<script>
import obj from "@/util/obj";
import Spinner from '@/apps/dashboard/shared/components/Spinner'
import TestingResultCard from '@/apps/dashboard/views/onboarding/components/TestingResultCard'
import api from '@/apps/dashboard/views/testing/api'
import func from "@/util/func";

export default {
    name: "TestingResultsSummary",
    components: {
        Spinner,
        TestingResultCard
    },
    props: {
        testingRunHexId: obj.strR
    },
    data () {
        return {
            tab: 0,
            items: ['High', 'Medium', 'Low'],
            testingRunSummary: null,
            testResults1: [
                {
                    method: "POST",
                    path: "/user/login",
                    severity: "High",
                    vulnerability: "Broken Object Level Authorization",
                    testName: "replace_auth_token"
                },
                {
                    method: "POST",
                    path: "/api/cards",
                    severity: "High",
                    vulnerability: "Broken Object Level Authorization",
                    testName: "replace_auth_token"
                },
                {
                    method: "PATCH",
                    path: "/api/reviews",
                    severity: "High",
                    vulnerability: "Broken Object Level Authorization",
                    testName: "replace_auth_token"
                }
            ],
            loading: true,
            refreshSummariesInterval: null,
            testingRunResults: null,
            refreshTestResultsInterval: null
        }
    },
    methods: {
        next() {
        },
        refreshSummaries() {
            return api.fetchTestingRunResultSummaries(0, func.timeNow(), this.testingRunHexId).then(resp => {
                let testingRunResultSummaries = resp.testingRunResultSummaries
                if (testingRunResultSummaries && testingRunResultSummaries.length > 0) {
                    this.testingRunSummary = testingRunResultSummaries.reduce((prev, current) => (+prev.startTimestamp > +current.startTimestamp) ? prev : current)
                }
            })
        },
    },
    async mounted() {
        await this.$store.dispatch('issues/fetchAllSubCategories')
        await this.refreshSummaries()

        if (!this.testingRunSummary) {
            this.refreshSummariesInterval = setInterval(() => {
                this.refreshSummaries().then(() => {
                if (this.testingRunResultSummaries.length !== 0) {
                    clearInterval(this.refreshSummariesInterval)
                }
                })
            }, 2000)
        }

        this.refreshTestResultsInterval = setInterval(() => {
            api.fetchTestingRunResults(this.testingRunSummary.hexId).then(resp => {
                this.testingRunResults = resp.testingRunResults
            })

            if (this.testingRunSummary && (this.testingRunSummary.state === "SCHEDULED" || this.testingRunSummary.state === "RUNNING")) {
                this.refreshSummaries()
            } else {
                clearInterval(this.refreshTestResultsInterval)
                this.loading = false
            }
        }, 5000)
    },
    computed: {
        testResults() {
            if (!this.testingRunResults) return null
            let subCatogoryMap = this.$store.state.issues.subCatogoryMap
            let subCategoryFromSourceConfigMap = this.$store.state.issues.subCategoryFromSourceConfigMap
            let temp = []

            this.testingRunResults.forEach((x) => {
                if (x["vulnerable"] && temp.length < 3) {
                    temp.push(
                        {
                            method: x['apiInfoKey']['method'],
                            path: func.convertToRelativePath(x["apiInfoKey"]["url"]),
                            severity: "High",
                            vulnerability: func.getRunResultCategory(x, subCatogoryMap, subCategoryFromSourceConfigMap, "displayName"),
                            testName: func.getRunResultSubCategory (x, subCategoryFromSourceConfigMap, subCatogoryMap, "issueDescription")
                        }
                    )
                }
            })

            return temp
        }
    }
}
</script>

<style lang="sass">

.testing-summary-div
    width: 490px
    height: 470px

.tabs
    background-color: #FAFAFA
    border-radius: 12px

.tab-selected
    background-color: rgba(71, 70, 106, 0.15)


.v-tab
    font-weight: 500
    font-size: 16px
    text-transform: none !important
    color: #000000 !important
    border-radius: 8px
    letter-spacing: normal

.v-tab.v-tab--active
    color: #47466A !important

.count-chip
    background-color: #ABABAB
    color: white
    margin: 2px 2px 2px 6px
    padding: 2px
    font-weight: 500
    font-size: 12px
    border-radius: 4px
    width: 22px
    height: 22px


.count-chip-selected
    background-color: #47466A

.spinner-div
    display: flex
    justify-content: center
    height: 100%
    align-items: center
</style>