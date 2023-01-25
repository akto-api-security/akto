<template>
    <div v-if="messagesBasic && messagesBasic.length > 0">
        <div>
            <div class="d-flex jc-end mr-3">
                <!-- <div v-if="jsonBasic.title" style="margin: auto 8px; color: #47466A">{{ jsonBasic.title }}</div> -->
                <v-btn v-if="messagesBasic.length > 1" icon @click="nextClicked">
                    <v-icon>$fas_angle-double-right</v-icon>
                </v-btn>
            </div>
            <div>
                <layout-with-tabs :tabsContent="getTabsContent()" title=""
                    :tabs="['Description', 'Original', 'Attempt']" ref="layoutWithTabs">
                    <template slot="Description">
                        <div class="description-title mt-4" :style="{ 'height': '500px' }"
                            v-if="issuesDetails === undefined || issuesDetails === null || Object.keys(issuesDetails).length === 0">
                            No vulnerabilities exists
                        </div>
                        <div v-else class="d-flex flex-column">
                            <div class="d-flex flex-column">
                                <span class="description-title mt-4">Issue summary</span>
                                <div class="mt-3 issue-summary-border">
                                    <v-container fluid class="ma-0 pa-0">
                                        <v-row :style="{ 'margin-top': '10px' }"
                                            v-for="(item, index) in issueSummaryTable" :key="index"
                                            class="mx-0 mb-0 pa-0">

                                            <v-col cols="2" class="ma-0 pa-0">
                                                <span class="description-content">{{ item.title }}</span>
                                            </v-col>

                                            <v-col v-if="item.title === 'Endpoint'" class="my-0 mr-0 ml-7 pa-0">
                                                <span class="issue-summary">{{ item.description.method }}</span>
                                                <span class="issue-summary">{{ item.description.url }}</span>
                                            </v-col>
                                            <v-col v-else class="my-0 mr-0 ml-7 pa-0">
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
                                                <v-chip :style="{ 'height': '24px !important' }" color="#47466AB2"
                                                    class="issue-summary mr-2" text-color="#FFFFFF"
                                                    :key="index"
                                                    v-for="(chipItem, index) in subCatogoryMap[issuesDetails.id.testSubCategory].issueTags">
                                                    {{ chipItem.name }}
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
                                        v-html="replaceTags(subCatogoryMap[issuesDetails.id.testSubCategory].issueDetails)"></span>
                                </div>
                            </div>
                            <div class="d-flex flex-column mt-4">
                                <span class="description-title">Impact</span>
                                <div class="mt-3">
                                    <span class="description-content"
                                        v-html="subCatogoryMap[issuesDetails.id.testSubCategory].issueImpact"></span>
                                </div>
                            </div>
                            <div v-if="similarlyAffectedIssues || similarlyAffectedIssues.length === 0" class="mt-4">
                                <span class="description-title">Api endpoints affected</span>
                                <table :style="{ 'width': '100%' }" class="mt-3 mb-3">
                                    <tr class="table-row" v-for="(item, index) in similarlyAffectedIssues" :key="index">
                                        <td class="table-column clickable">
                                            <span
                                                class="description-content mr-1 ml-3">{{ item.id.apiInfoKey.method }}</span>
                                            <span class="description-content">{{ item.id.apiInfoKey.url }}</span>
                                        </td>
                                    </tr>
                                </table>
                                <!-- <span v-if="!isTestingPage" class="issue-summary clickable-line ml-4" @click="$emit('showAllIssueByCategory', subCatogoryMap[issuesDetails.id.testSubCategory])">Show all endpoints</span> -->
                            </div>
                            <div class="d-flex flex-column mt-4">
                                <span class="description-title">References</span>
                                <ul class="mt-3">
                                    <li class="description-content mt-2 "
                                        v-for="item in subCatogoryMap[issuesDetails.id.testSubCategory].references">
                                        <span><a :href="item" target="_blank" class="clickable-line">{{
                                            item
                                        }}</a></span>
                                    </li>
                                </ul>
                            </div>
                        </div>
                    </template>
                    <template slot="Original">
                        <div style="margin: 24px">
                            <sample-data :json="jsonAdvance" requestTitle="Original Request"
                                responseTitle="Original Response" />
                        </div>
                    </template>
                    <template slot="Attempt" v-if="jsonAdvance && jsonAdvance['message']">
                        <div :style="{ 'height': '500px' }">
                            <div v-if="jsonBasic['errors']" class="test-errors-class">
                                {{ this.jsonBasic["errors"] }}
                            </div>
                            <div style="margin: 24px">
                                <span>Test response matches {{ percentageMatch }}% with original API response</span>
                                <sample-data v-if="jsonBasic && jsonBasic['message']" :json="jsonBasic"
                                    requestTitle="Test Request" responseTitle="Test Response" />
                            </div>
                        </div>
                    </template>
                </layout-with-tabs>
            </div>
        </div>
    </div>
    <div v-else class="empty-container">
        No samples values saved yet!
    </div>
</template>

<script>
import obj from "@/util/obj";
import LayoutWithTabs from '@/apps/dashboard/layouts/LayoutWithTabs'
import SampleData from "../../../shared/components/SampleData";
import TestResultDetails from "./TestResultDetails";
import func from "@/util/func";

export default {
    name: "TestResultsDialog",
    components: {
        SampleData,
        LayoutWithTabs,
        TestResultDetails
    },
    props: {
        testingRunResult: obj.objR,
        issuesDetails: obj.objN,
        subCatogoryMap: obj.objN,
        mapCollectionIdToName: obj.objN,
        similarlyAffectedIssues: obj.arrN,
        isTestingPage: obj.boolN
    },
    data() {
        return {
            currentIndex: 0,
            issueSummaryTable: []
        }
    },
    methods: {
        getTabsContent() {
            if (this.messagesBasic.length > 1) {
                return { 'Attempt': this.messagesBasic.length }
            }
            return undefined
        },
        replaceTags(details) {
            return details.replace(/{{percentageMatch}}/g, this.percentageMatch)
        },
        nextClicked() {
            this.currentIndex = (++this.currentIndex) % this.messagesBasic.length
            this.$refs.layoutWithTabs.setTabWithName('Attempt')
        },
        buildHighlightPaths(paramInfoList) {
            if (!paramInfoList) paramInfoList = []
            let highlightPaths = paramInfoList.map((x) => {
                let asterisk = x.isPrivate
                x["highlightValue"] = {
                    "value": "unique: " + x.uniqueCount + " public: " + x.publicCount,
                    "asterisk": asterisk,
                    "highlight": false
                }
                return x
            })

            return highlightPaths
        }
    },
    watch: {
        testingRunResult(n, o) {
            this.currentIndex = 0
        },
        mapCollectionIdToName(newValue) {
            this.issueSummaryTable[4].description = newValue[this.issuesDetails.id.apiInfoKey.apiCollectionId]
        },
        issuesDetails(issuesDetails) {
            if (issuesDetails) {
                this.issueSummaryTable = [
                    {
                        title: 'Issue category',
                        description: this.subCatogoryMap[issuesDetails.id.testSubCategory].superCategory.displayName
                    },
                    {
                        title: 'Test run',
                        description: this.subCatogoryMap[issuesDetails.id.testSubCategory].testName
                    },
                    {
                        title: 'Severity',
                        description: this.subCatogoryMap[issuesDetails.id.testSubCategory].superCategory.severity._name
                    },
                    {
                        title: 'Endpoint',
                        description: {
                            method: issuesDetails.id.apiInfoKey.method,
                            url: issuesDetails.id.apiInfoKey.url
                        }
                    },
                    {
                        title: 'Collection',
                        description: this.mapCollectionIdToName[issuesDetails.id.apiInfoKey.apiCollectionId]
                    }
                ]
            }
        }
    },
    computed: {
        messagesBasic() {
            let testSubType = this.testingRunResult["testSubType"]
            return this.testingRunResult["testResults"].map(x => { return { message: x.message, title: testSubType, highlightPaths: [], errors: x.errors } })
        },
        messagesAdvance() {
            let testSubType = this.testingRunResult["testSubType"]
            let singleTypeInfos = this.testingRunResult["singleTypeInfos"]
            let highlightPaths = this.buildHighlightPaths(singleTypeInfos);
            return this.testingRunResult["testResults"].map(x => { return { message: x.originalMessage, title: testSubType, highlightPaths: highlightPaths, errors: x.errors, percentageMatch: x.percentageMatch } })
        },
        jsonBasic: function () {
            if (this.testingRunResult == null) return null
            let currentMessage = this.messagesBasic[this.currentIndex]
            return {
                "message": JSON.parse(currentMessage["message"]),
                title: currentMessage["title"],
                "highlightPaths": currentMessage["highlightPaths"],
                "errors": currentMessage["errors"].map(x => x.message).join(", ")
            }
        },
        percentageMatch: function () {
            if (this.testingRunResult == null) return null
            let currentMessage = this.messagesAdvance[this.currentIndex]
            try {
                return func.prettifyShort(currentMessage["percentageMatch"])
            } catch (e) {
                console.log(e);
                return null
            }
        },
        jsonAdvance: function () {
            if (this.testingRunResult == null) return null
            let currentMessage = this.messagesAdvance[this.currentIndex]
            if (!currentMessage) return null
            return {
                "message": JSON.parse(currentMessage["message"]),
                title: currentMessage["title"],
                "highlightPaths": currentMessage["highlightPaths"],
            }
        }
    }
}
</script>

<style lang="sass" scoped>
.test-errors-class
  padding: 24px 0px 0px 24px

.table-column
  padding: 4px 8px !important
  color: #47466A

.table-row
  position: relative
  background: rgba(71, 70, 106, 0.03)
  line-height: 32px

  &:hover
      background-color: #edecf0 !important
</style>

<style scoped>
.description-title {
    font-size: 14px !important;
    font-weight: 500;
    color: #47466A;
}

.description-content {
    font-size: 12px !important;
    font-weight: 400;
    color: #47466A;
}

.issue-summary {
    font-size: 12px !important;
    font-weight: 500;
    color: #47466A;
}

.issue-summary-border {
    border-width: 1px 0px;
    border-color: #DADAE1;
    border-style: solid;
}
</style>
