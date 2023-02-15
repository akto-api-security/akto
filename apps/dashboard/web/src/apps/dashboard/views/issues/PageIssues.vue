<template>
    <simple-layout title="Issues">
        <issues-filters :filterStatus="filterStatus" :issues="issues" :filterCollectionsId="filterCollectionsId"
            :filterSeverity="filterSeverity" :filterSubCategory1="filterSubCategory1"
            :selectedIssueIds="selectedIssueIds" :startEpoch="startEpoch" :issuesCategories="issuesCategories"
            :categoryToSubCategories="categoryToSubCategories">
        </issues-filters>
        <spinner v-if="loading">
        </spinner>
        <div v-else>
            <issues-dialog :similarlyAffectedIssues="similarlyAffectedIssues" :openDetailsDialog="dialogBox"
                :testingRunResult="testingRunResult" :subCatogoryMap="subCatogoryMap" :issue="dialogBoxIssue"
                @closeDialogBox="(dialogBox = false)">
            </issues-dialog>
            <template v-for="(issue, index) in issues" >
                <issue-box v-if="getCategoryName(issue.id)"  :key="index" :creationTime="issue.creationTime"
                    :method="issue.id.apiInfoKey.method" :endpoint="issue.id.apiInfoKey.url" :severity="issue.severity"
                    :collectionName="getCollectionName(issue.id.apiInfoKey.apiCollectionId)"
                    :categoryName="getCategoryName(issue.id)"
                    :categoryDescription="getCategoryDescription(issue.id)"
                    :testType="getTestType(issue.id.testErrorSource)" :issueId="issue.id"
                    :issueStatus="issue.testRunIssueStatus" :ignoreReason="issue.ignoreReason"
                    :issueChecked="(selectedIssueIds.indexOf(issue.id) > -1)" :filterStatus="filterStatus"
                    @clickedIssueCheckbox="updateSelectedIssueIds" @openDialogBox="openDialogBox">
                </issue-box>
            </template>
            <v-pagination color="var(--v-themeColor-base)" v-model="currentPageIndex" v-if="totalPages > 1"
                :length="totalPages" prev-icon="$fas_angle-left" next-icon="$fas_angle-right">
            </v-pagination>
        </div>
    </simple-layout>
</template>

<script>
import { mapState } from 'vuex'
import SimpleLayout from '@/apps/dashboard/layouts/SimpleLayout'
import IssueBox from './components/IssueBox'
import IssuesFilters from './components/IssuesFilters'
import IssuesDialog from './components/IssuesDialog'
import Spinner from '@/apps/dashboard/shared/components/Spinner'
import api from './api'



export default {
    name: "PageIssues",
    components: {
        SimpleLayout,
        IssueBox,
        Spinner,
        IssuesFilters,
        IssuesDialog
    },
    data() {
        return {
            currentPageIndex: 1,
            dialogBox: false,
            issuesCategories: [],
            categoryToSubCategories: {},
            dialogBoxIssue: {},
            similarlyAffectedIssues: []
        }
    },
    watch: {
        currentPageIndex(newValue) {
            this.$store.commit('issues/updateCurrentPage', { 'pageIndex': newValue })
            this.$store.dispatch('issues/loadIssues')
        }
    },
    computed: {
        ...mapState('issues', ['issues', 'loading', 'currentPage', 'limit'
            , 'totalIssuesCount', 'filterStatus', 'filterCollectionsId', 'filterSeverity'
            , 'filterSubCategory1', 'startEpoch', 'selectedIssueIds', 'testingRunResult', 'subCatogoryMap']),

        totalPages() {
            if (!this.totalIssuesCount && this.totalIssuesCount === 0) {
                return 0;
            }
            return Math.ceil(this.totalIssuesCount / this.limit);
        },
        mapCollectionIdToName() {
            return this.$store.state.collections.apiCollections.reduce((m, e) => {
                m[e.id] = e.displayName
                return m
            }, {})
        }
    },
    async mounted() {
        this.$store.dispatch('issues/loadIssues')
        await this.$store.dispatch('issues/fetchAllSubCategories')

        let store = {}
        let result = []
        Object.values(this.$store.state.issues.subCatogoryMap).forEach((x) => {
            let superCategory = x.superCategory
            if (!store[superCategory.name]) {
                result.push({ "title": superCategory.displayName, "value": superCategory.name })
                store[superCategory.name] = []
            }
            store[superCategory.name].push(x._name);
        })

        this.issuesCategories = [].concat(result)
        this.categoryToSubCategories = store

    },
    methods: {
        async openDialogBox({ issueId }) {
            await this.$store.dispatch('issues/loadTestingResult', { issueId })
            await api.fetchAffectedEndpoints(issueId).then(resp => {
                this.similarlyAffectedIssues = resp['similarlyAffectedIssues']
                this.issues.forEach((item) => {
                    if (issueId === item.id) {
                        this.dialogBoxIssue = item
                    }
                })
                this.dialogBox = true
            })
        },
        updateSelectedIssueIds({ issueId, checked }) {
            let selectedIssueIds = this.selectedIssueIds
            let index = selectedIssueIds.indexOf(issueId)
            if (checked) {// filter checked case
                if (index < 0) {
                    selectedIssueIds.push(issueId)
                }
            } else {// filter unchecked case
                if (index > -1) {
                    selectedIssueIds.splice(index, 1)
                }
            }
            this.$store.commit('issues/updateSelectedIssueIds', { selectedIssueIds })
        },
        getTestType(name) {
            switch (name) {
                case 'AUTOMATED_TESTING':
                    return 'testing';
                case 'RUNTIME':
                    return 'runtime'
                default:
                    return ''
            }
        },
        getCategoryName(id) {
            if (this.$store.state.issues.subCatogoryMap[id.testSubCategory] === undefined) {//Config case
                let a = this.$store.state.issues.subCategoryFromSourceConfigMap[id.testCategoryFromSourceConfig]
                return a ?  a.subcategory : null;
            }
            return this.$store.state.issues.subCatogoryMap[id.testSubCategory].testName;
        },
        getCategoryDescription(id) {
            if (this.$store.state.issues.subCatogoryMap[id.testSubCategory] === undefined) {//Config case
                let a = this.$store.state.issues.subCategoryFromSourceConfigMap[id.testCategoryFromSourceConfig]
                return a ? a.description : null;
            }
            return this.$store.state.issues.subCatogoryMap[id.testSubCategory].issueDescription
        },
        getCollectionName(collectionId) {
            return this.mapCollectionIdToName[collectionId];
        }
    }
}

</script>

<style scoped >

</style>