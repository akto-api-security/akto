<template>
    <div class="ma-5">
        <div class="d-flex jc-sb"><v-icon size="40" color="var(--themeColor)">$aktoWhite</v-icon><span class="title-1">Akto Vulnerabilities Report</span></div>
        <div class="mt-4">
            <span class="summary-alert my-3">
                Summary of Alerts
            </span>
            <table class="severity-global-table my-3">
                <tr class="header-row">
                    <th>Severity</th>
                    <th>Vulnerable APIs </th>
                </tr>
                <tr class="data-row">
                    <td>High</td>
                    <td>{{ high }}</td>
                </tr>
                <tr class="data-row">
                    <td>Medium</td>
                    <td>{{ medium }}</td>
                </tr>
                <tr class="data-row">
                    <td>Low</td>
                    <td>{{ low }}</td>
                </tr>
            </table>

            <span>vulnerabilities Details</span>

            <div v-for="testSubType in categoryVsVulnerabilitiesMap">
                <table>
                    <tr>
                        <th>vulnerability</th>
                        <th>{{ testSubType.category.testName }}</th>
                    </tr>
                </table>
                <div class="d-flex flex-column">
                    <div class="d-flex flex-column">
                        <span class="description-title mt-4">Issue summary</span>
                        <div class="mt-3 issue-summary-border">
                            <v-container fluid class="ma-0 pa-0">
                                <v-row :style="{ 'margin-top': '10px' }" v-for="(item, index) in getIssueSummaryTable(testSubType)"
                                    :key="index" class="mx-0 mb-0 pa-0">

                                    <v-col cols="2" class="ma-0 pa-0">
                                        <span class="description-content">{{ item.title }}</span>
                                    </v-col>

                                    <v-col class="my-0 mr-0 ml-7 pa-0">
                                        <span class="issue-summary">
                                            {{ item.description }}
                                        </span>
                                    </v-col>
                                </v-row>
                                <v-row class="mx-0 pa-0"
                                    :style="{ 'margin-top': '10px', 'margin-bottom': '10px' }">
                                    <v-col cols="2" class="ma-0 pa-0">
                                        <span class="description-content">Tags</span>
                                    </v-col>
                                    <v-col class="my-0 mr-0 ml-7 pa-0">
                                        <v-chip :style="{ 'height': '24px !important' }" color="var(--themeColorDark6)"
                                            class="issue-summary mr-2" text-color="var(--white)" :key="index1"
                                            v-for="(chipItem, index1) in testSubType.category.issueTags">
                                            {{ chipItem }}
                                        </v-chip>
                                    </v-col>
                                </v-row>
                            </v-container>
                        </div>
                    </div>
                    <div class="d-flex flex-column mt-4">
                        <span class="description-title">Issue Details</span>
                        <div class="mt-3">
                            <span class="description-content"
                                v-html="replaceTags(testSubType.category.issueDetails, testSubType.category.vulnerableTestingRunResults)"></span>
                        </div>
                    </div>
                    <div class="d-flex flex-column mt-4">
                        <span class="description-title">Impact</span>
                        <div class="mt-3">
                            <span class="description-content"
                                v-html="testSubType.category.issueImpact"></span>
                        </div>
                    </div>
                    <div class="d-flex flex-column mt-4">
                        <span class="description-title">References</span>
                        <ul class="mt-3">
                            <li class="description-content mt-2 "
                                v-for="item in testSubType.category.references">
                                <span><a :href="item" target="_blank" class="clickable-line">{{
                                    item
                                }}</a></span>
                            </li>
                        </ul>
                    </div>
                </div>
                <table v-for="testingRun in testSubType.category.vulnerableTestingRunResults">
                    <tr>
                        <th>Vulnerable Endpoint</th>
                        <th>{{ testingRun.apiInfoKey.url }}</th>
                    </tr>
                    <tr>
                        <th>Original Request</th>
                        <th>Attempt</th>
                    </tr>
                    <tr v-for="testRun in testingRun.testResults">
                        <td>{{ getOriginalCurl(testRun.message) }}</td>
                        <td>{{ getOriginalCurl(testRun.originalMessage) }}</td>
                    </tr>

                </table>
            </div>
        </div>
    </div>
</template>

<script>
import obj from "@/util/obj"
import api from '../api'
import inventoryApi from "@/apps/dashboard/views/observe/inventory/api";
import func from "@/util/func";

export default {
    name: "PDFExportHTML",
    props: {
        testingRunResultSummaries: obj.arrR
    },
    data() {
        return {
            vulnerableTestingRunResults: [],
            high: 0,
            medium: 0,
            low: 0,
            categoryVsVulnerabilitiesMap: [],
            sampleDataVsCurlMap : {}
        }
    },
    methods: {
        getOriginalCurl(message) {
            return this.sampleDataVsCurlMap[message]
        },
        replaceTags(details, vulnerableRequests) {
            let percentageMatch = 0;
            vulnerableRequests.forEach((request) => {
                let testRun = request['testResults']
                testRun.forEach((runResult) => {
                    if (percentageMatch < runResult.percentageMatch) {
                        percentageMatch = runResult.percentageMatch
                    }
                })
            })
            return details.replace(/{{percentageMatch}}/g, func.prettifyShort(percentageMatch))
        },
        getIssueSummaryTable(testSubType) {
             var issueTable = [{
                    title: "Issue category",
                    description: testSubType.category.superCategory.displayName
                },
                {
                    title: "Test run",
                    description: testSubType.category.testName
                },
                {
                    title: "Severity",
                    description: testSubType.category.superCategory.severity._name
                }
            ]
            return issueTable
        },
        refreshVulnerabilityCount(testingRunResults) {
            let categoryVsVulMap = {}
            let sampleDataMsgList = []
            testingRunResults.forEach((testingRun) => {

                let subtype = testingRun['testSubType']
                let subCategory = this.subCatogoryMap[subtype]
                let severity = subCategory['superCategory']['severity']['_name']
                let severityIndex = 0;
                switch (severity) {
                    case 'HIGH': ++this.high
                        severityIndex = 2
                        break;
                    case 'MEDIUM': ++this.medium
                        severityIndex = 1
                        break;
                    case 'LOW': ++this.low
                        severityIndex = 0
                        break;
                }

                let vulnerabilities = categoryVsVulMap[subtype]
                if (vulnerabilities === undefined) {
                    vulnerabilities = JSON.parse(JSON.stringify(subCategory))
                }
                let vulnerableTestingRunResults = vulnerabilities["vulnerableTestingRunResults"]
                if (vulnerableTestingRunResults === undefined) {
                    vulnerableTestingRunResults = []
                }
                vulnerableTestingRunResults.push(testingRun)
                vulnerabilities['vulnerableTestingRunResults'] = vulnerableTestingRunResults
                vulnerabilities['severityIndex'] = severityIndex
                categoryVsVulMap[subtype] = vulnerabilities
                let testResults = testingRun.testResults
                testResults.forEach((testResult) => {
                    sampleDataMsgList.push(testResult.message)
                    sampleDataMsgList.push(testResult.originalMessage)
                })
            })
            Object.keys(categoryVsVulMap).forEach((category) => {
                let obj = categoryVsVulMap[category]
                this.categoryVsVulnerabilitiesMap.push({ category: obj })
            })

            let compare = function (a, b) {
                let severityA = a[Object.keys(a)[0]]['severityIndex']
                let severityB = b[Object.keys(a)[0]]['severityIndex']
                return severityB - severityA
            }
            this.categoryVsVulnerabilitiesMap.sort(compare)
            this.updateSampleDataVsCurlMap(sampleDataMsgList)
        },
        async updateSampleDataVsCurlMap(sampleDataMsgList) {
            let _this = this
            await inventoryApi.convertSampleDataListToCurl(sampleDataMsgList).then(resp => {
                _this.sampleDataVsCurlMap = resp.sampleDataVsCurlMap
            })
        },
        async fetchVulnerableTestingRunResults() {
            let _this = this
            await api.fetchVulnerableTestingRunResults(this.testingRunResultSummaries[0].hexId).then(resp => {
                _this.vulnerableTestingRunResults = resp.testingRunResults
                _this.refreshVulnerabilityCount(_this.vulnerableTestingRunResults)
            })
        }
    },
    async mounted() {
        await this.$store.dispatch('issues/fetchAllSubCategories')
        let interval = setInterval(() => {
            if (this.testingRunResultSummaries  && this.testingRunResultSummaries.length > 0 && this.testingRunResultSummaries[this.testingRunResultSummaries.length - 1].hexId) {
                this.fetchVulnerableTestingRunResults()
                clearInterval(interval)
            }
        }, 1000)
    },
    computed: {
        subCatogoryMap: {
            get() {
                return this.$store.state.issues.subCatogoryMap
            }
        }
    }
}
</script>

<style scoped lang="css">
.title-1 {
    margin: auto;
    font-weight: 600;
    font-size:24px;
    color: var(--themeColorDark)
}
.summary-alert {
    font-weight: 500;
    font-size:20px;
    color: var(--themeColorDark)
}
.severity-global-table {
    width: 40%;
    text-align: right;
    border: 2px solid var(--themeColorDark);
    border-radius: 4px;
    padding: 2px;
}
.header-row{
    display: flex;
    background: #666666;
    color: var(--white) ;
    border-bottom: 4px solid var(--white);
}

.header-row th{
    width: 50%;
    display: flex;
    justify-content: center;
    border-right: 4px solid;
}

.data-row{
    display: flex;
    background: #e8e8e8;
    border-bottom: 4px solid var(--white);
}

.data-row td{
    width: 50%;
    justify-content: center;
    display: flex;
    border-right: 4px solid var(--white);
}

.severity-global-table .th .td{
    border-bottom: 1px solid var(--themeColorDark);
}
.description-title {
    font-size: 14px !important;
    font-weight: 500;
    color: var(--themeColorDark);
}

.issue-summary-border {
    border-width: 1px 0px;
    border-color: var(--lighten2);
    border-style: solid;
}

.description-content {
    font-size: 12px !important;
    font-weight: 400;
    color: var(--themeColorDark);
}

.issue-summary {
    font-size: 12px !important;
    font-weight: 500;
    color: var(--themeColorDark);
}

.table-column {
    padding: 4px 8px !important;
    color: var(--themeColorDark);
}
.logo {
    height: 35px;
}
</style>