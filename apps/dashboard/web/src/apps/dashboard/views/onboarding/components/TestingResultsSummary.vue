<template>
    <div class="pa-5 pb-0 testing-summary-div">
        <div v-if="!this.testResults" class="spinner-div height-class">
            <div class="main-loading-text">Scheduling test</div>
            <spinner :size="12"/>
        </div>
        <div v-else>
            <div class="loading-bar" v-if="loading">
                <v-progress-linear :indeterminate="true" height="8" color="var(--themeColor)" rounded></v-progress-linear>
            </div>

            <v-tabs
                v-model="tab"
                grow
                class="severity-tabs"
                :hide-slider="true"
            >
                <v-tab
                    v-for="(item,idx) in this.items"
                    :key="item"
                    active-class="tab-selected"
                >
                    {{ item }}
                    <div :class="idx === tab ? 'count-chip count-chip-selected' : 'count-chip'">
                        {{ testResults[idx].length }}
                    </div>
                </v-tab>
                <v-tabs-items v-model="tab" v-if="this.testResults">
                    <div class="height-class tab-content">
                        <div v-if="this.testResults[tab] && this.testResults[tab].length > 0" class="fd-column" style="gap: 12px">
                            <div v-for="testResult in this.testResults[tab]">
                                <testing-result-card
                                    :method= "testResult['method']"
                                    :path= "testResult['path']"
                                    :severity= "testResult['severity']"
                                    :vulnerability= "testResult['vulnerability']"
                                    :testName= "testResult['testName']"
                                />
                            </div>
                        </div>
                        <div v-else class="zero-div">
                            <div class="d-flex jc-sa">
                                <img src="/public/zero_logo.svg" class="zero-svg"/>
                            </div>
                            <div class="zero-issues-text">We have found 0 {{ items[tab].toLowerCase()}} issues, YAY!</div>
                        </div>
                    </div>
                </v-tabs-items>
            </v-tabs>
        </div>
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
                if (this.testingRunResultSummaries &&  this.testingRunResultSummaries.length !== 0) {
                    clearInterval(this.refreshSummariesInterval)
                }
                })
            }, 1000)
        }

        this.refreshTestResultsInterval = setInterval(() => {
            if (!this.testingRunSummary) return
            api.fetchTestingRunResults(this.testingRunSummary.hexId).then(resp => {
                this.testingRunResults = resp.testingRunResults
            })

            if (this.testingRunSummary && (this.testingRunSummary.state === "SCHEDULED" || this.testingRunSummary.state === "RUNNING")) {
                this.refreshSummaries()
            } else {
                clearInterval(this.refreshTestResultsInterval)
                this.loading = false
            }
        }, 1000)
    },
    computed: {
        testResults() {
            if (!this.testingRunResults) return null
            let subCatogoryMap = this.$store.state.issues.subCatogoryMap
            let subCategoryFromSourceConfigMap = this.$store.state.issues.subCategoryFromSourceConfigMap
            let temp = []

            let t = [...this.testingRunResults]

            t.reverse().forEach((x) => {
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

            return [temp, [], []]
        }
    },
    destroyed() {
        clearInterval(this.refreshSummariesInterval)
        clearInterval(this.refreshTestResultsInterval)
    }
}
</script>

<style lang="sass">

.testing-summary-div
    width: 490px


.severity-tabs
    background-color: #FAFAFA
    border-radius: 12px

.tab-selected
    background-color: rgba(71, 70, 106, 0.15)


.v-tab
    font-weight: 500
    font-size: 16px
    text-transform: none !important
    color: var(--black) !important
    border-radius: 8px
    letter-spacing: normal

.tab-content
    padding-top: 32px

.v-tab.v-tab--active
    color: var(--themeColorDark) !important

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
    background-color: var(--themeColorDark)

.spinner-div
    display: flex
    justify-content: center
    align-items: center

.height-class
    height: 390px

.main-loading-text
    font-weight: 500
    font-size: 16px
    color: var(--themeColorDark)
    margin: 0px 6px 0px 0px

.loading-bar
    padding: 0px 0px 32px 0px
    display: flex
    align-items: center

.zero-issues-text
    font-weight: 500
    font-size: 16px
    line-height: 24px
    text-align: center
    color: var(--themeColorDark)

.zero-div
    display: flex
    justify-content: center
    flex-direction: column
    height: 90%

.zero-svg
    width: 158.5px
    height: 197.16px
</style>