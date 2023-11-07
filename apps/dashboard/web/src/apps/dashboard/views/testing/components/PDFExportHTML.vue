<template>
    <div class="ma-5">
        <div class="d-flex jc-sb"><v-icon size="40" color="var(--themeColor)">$aktoWhite</v-icon><span class="title-1">Akto Vulnerabilities Report</span></div>
        <div class="mt-4">
            <span class="summary-alert my-3">
                Summary of Alerts
            </span>
            <table class="severity-global-table my-3" style="width:40%;">
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

            <span class="summary-alert">Vulnerabilities details</span>
            <div class="vulnerability-div">
                <div v-for="testSubType in categoryVsVulnerabilitiesMap" style="border: 2px solid var(--themeColorDark);">
                    <div class="d-flex" style="border-bottom: 1px solid var(--themeColorDark);" :style="getSeverityColor(testSubType.category.severityIndex)">
                        <span class="vulnerable-title">Vulnerability</span>
                        <span class="vulnerable-data">{{ testSubType.category.testName }}</span>
                    </div>

                    <div class="d-flex flex-column" style="padding: 2px 4px; border-bottom: 1px solid var(--themeColorDark);">
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
                                    <v-row class="mx-0 pa-0"
                                        :style="{ 'margin-top': '10px', 'margin-bottom': '10px' }">
                                        <v-col cols="2" class="ma-0 pa-0">
                                            <span class="description-content">CWE</span>
                                        </v-col>
                                        <v-col class="my-0 mr-0 ml-7 pa-0">
                                            <v-chip :style="{ 'height': '24px !important' }" color="var(--themeColorDark6)"
                                                class="issue-summary mr-2" text-color="var(--white)" :key="index1"
                                                v-for="(chipItem, index1) in testSubType.category.cwe">
                                                {{ chipItem }}
                                            </v-chip>
                                        </v-col>
                                    </v-row>
                                    <v-row class="mx-0 pa-0"
                                        :style="{ 'margin-top': '10px', 'margin-bottom': '10px' }"
                                        v-if="testSubType.category.cve">
                                        <v-col cols="2" class="ma-0 pa-0">
                                            <span class="description-content">CVE</span>
                                        </v-col>
                                        <v-col class="my-0 mr-0 ml-7 pa-0">
                                            <v-chip :style="{ 'height': '24px !important' }" color="var(--themeColorDark6)"
                                                class="issue-summary mr-2" text-color="var(--white)" :key="index1"
                                                v-for="(chipItem, index1) in testSubType.category.cve">
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
                    <div class="attempts-div" v-for="testingRun in testSubType.category.vulnerableTestingRunResults">
                        <div class="row-div-1">
                            <span class="api-text">
                                Vulnerable endpoint : 
                            </span>
                            <span class="url-text">
                                {{ testingRun.apiInfoKey.url }}
                            </span>
                        </div>
                        <div v-for="testRun in testingRun.testResults">
                            <div class="row-div">
                                <span class="title-name" style="font-weight: 500;">
                                    Original request
                                </span>
                                <span class="url-name" style="font-weight: 500;">
                                    Attempt
                                </span>
                            </div>
                            <div class="row-div">
                                <span class="message" style="border-right: 1px solid var(--themeColorDark10);">
                                    {{ getTruncatedString(getOriginalCurl(testRun.originalMessage)) }}
                                </span>
                                <span class="message">
                                    {{ getTruncatedString(getOriginalCurl(testRun.message)) }}
                                </span>
                            </div>
                            <div class="row-div">
                                <span class="title-name" style="font-weight: 500;">
                                    Original Response
                                </span>
                                <span class="url-name" style="font-weight: 500;">
                                    Attempt Response
                                </span>
                            </div>
                            <div class="row-div">
                                <span class="message" style="border-right: 1px solid var(--themeColorDark10);">
                                    {{ getTruncatedString(getResponse(testRun.originalMessage)) }}
                                </span>
                                <span class="message">
                                    {{ getTruncatedString(getResponse(testRun.message)) }}
                                </span>
                            </div>
                        </div>
                    </div>
                </div>
            </div>
        </div>
    </div>
</template>

<script>
import obj from "@/util/obj"
import api from '../api'
import func from "@/util/func"
import issuesApi from '../../issues/api'

export default {
    name: "PDFExportHTML",
    props: {
        testingRunResultSummaryHexId: obj.strN,
        issuesFilters: obj.strN
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
        getTruncatedString(str) {
            if (str && str.length > 3000) {
                return str.substr(0, 3000) + '  .........';
            }
            return str;
        },
        getSeverityColor(severityIndex) {
            switch (severityIndex) {
                case 2: return {'backgroundColor' : "var(--hexColor33)"}
                case 1:  return {'backgroundColor' : "var(--hexColor34)"}
                case 0: return {'backgroundColor' : "var(--hexColor35)"}
            }  
         },

        getResponse(message) {
            let messageJson = JSON.parse(message)
            if (messageJson['response']) {
                return JSON.stringify(messageJson['response'])
            }
            return JSON.stringify({"statusCode":messageJson['statusCode'],  "body": messageJson['responsePayload'],   "headers": messageJson['responseHeaders']})
        },
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
        createVulnerabilityMap(testingRunResults) {
            let categoryVsVulMap = {}
            testingRunResults.forEach((testingRun) => {

                let subtype = testingRun['testSubType']
                let subCategory = this.subCatogoryMap[subtype]
                if (!subCategory) {
                    return
                }
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
        },
        async fetchVulnerableTestingRunResults() {
            let _this = this
            let vulnerableTestingRunResults = []
            let sampleDataVsCurlMap = {}
            let skip = 0
            if (this.testingRunResultSummaryHexId) {
                while (true) {
                    let testingRunCountsFromDB = 0
                    await api.fetchVulnerableTestingRunResults(this.testingRunResultSummaryHexId, skip).then(resp => {
                        vulnerableTestingRunResults = vulnerableTestingRunResults.concat(resp.testingRunResults)
                        testingRunCountsFromDB = resp.testingRunResults.length
                        sampleDataVsCurlMap = {...sampleDataVsCurlMap, ...resp.sampleDataVsCurlMap}
                    })
                    skip += 50
                    if (testingRunCountsFromDB < 50) {
                        //EOF: break as no further documents exists
                        break
                    }
                }
            } else if (this.issuesFilters) {
                while (true) {
                    let testingRunCountsFromDB = 0
                    let filters = JSON.parse(atob(this.issuesFilters))
                    await issuesApi.fetchVulnerableTestingRunResultsFromIssues(filters, skip).then(resp => {
                        vulnerableTestingRunResults = vulnerableTestingRunResults.concat(resp.testingRunResults)
                        testingRunCountsFromDB = resp.totalIssuesCount
                        sampleDataVsCurlMap = {...sampleDataVsCurlMap, ...resp.sampleDataVsCurlMap}
                    })
                    skip += 50
                    if (testingRunCountsFromDB < 50 || skip >= 1000) {
                        //EOF: break as no further documents exists
                        break
                    }
                }
            }
            this.sampleDataVsCurlMap = sampleDataVsCurlMap
            this.vulnerableTestingRunResults = vulnerableTestingRunResults
            this.createVulnerabilityMap(_this.vulnerableTestingRunResults)
        }
    },
    async mounted() {
        await this.$store.dispatch('issues/fetchAllSubCategories')
        this.fetchVulnerableTestingRunResults()
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
    border: 2px solid var(--themeColorDark);
    border-radius: 4px;
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

.vulnerable-title{
    font-size: 16px;
    font-weight: 500;
    border-right: 2px solid var(--themeColorDark);
    padding:2px 4px;
    width: 35%;
}
.vulnerable-data{
    padding: 2px 4px;
}

.vulnerability-div{
    display: flex;
    flex-direction: column;
    gap: 10px;
}

.attempts-div {
    display: flex;
    flex-direction: column;
    margin: 4px 8px;
    border: 1px solid var(--themeColorDark10);
}
.row-div-1{
    display: flex;
    border-bottom: 1px solid var(--themeColorDark10);
    background: var(--hexColor23);
    gap: 10px;
    padding: 0 8px;
}

.url-text{
    font-weight: 500;
    color: var(--themeColorDark);
}

.api-text{
    font-weight: 600;
    color: var(--themeColorDark);
}
.row-div{
    display: flex;
    border-bottom: 1px solid var(--themeColorDark10);
}
.row-div:last-child{
    border-bottom: none !important;
}
.title-name {
    width: 50%;
    font-size: 16px;
    color: var(--themeColorDark);
    padding: 0 4px;
    justify-content: center;
    display: flex;
    border-right: 1px solid var(--themeColorDark10);
}
.url-name {
    justify-content: center;
    display: flex;
    width: 50%;
    color: var(--themeColorDark);
}

.row-div-1 .title-name{
    border-right: none !important;
}
.message {
    width: 50%;
    font-size: 12px;
    padding: 4px;
    word-break: break-all;
}
</style>