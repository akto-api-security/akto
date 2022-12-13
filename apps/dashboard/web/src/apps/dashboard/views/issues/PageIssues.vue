<template>
    <simple-layout title="Issues">
        <issues-filters :filterStatus="filterStatus" :issues="issues" :filterCollectionsId="filterCollectionsId"
            :filterSeverity="filterSeverity" :filterSubCategory1="filterSubCategory1"
            :selectedIssueIds="selectedIssueIds" :startEpoch="startEpoch">
        </issues-filters>
        <spinner v-if="loading">
        </spinner>
        <div v-else>
            <issues-dialog 
                :openDetailsDialog="dialogBox"
                :testingRunResult="testingRunResult"
                @closeDialogBox="(dialogBox = false)"
            >
            </issues-dialog>
            <issue-box v-for="(issue, index) in issues" :key="index" :creationTime="issue.creationTime"
                :method="issue.id.apiInfoKey.method._name" :endpoint="issue.id.apiInfoKey.url" :severity="issue.severity._name"
                :collectionName="getCollectionName(issue.id.apiInfoKey.apiCollectionId)"
                :categoryName="issue.id.testSubCategory.superCategory.displayName"
                :categoryDescription="issue.id.testSubCategory.issueDescription"
                :testType="issue.id.testErrorSource.name" :issueId="issue.id"
                :issueStatus="issue.testRunIssueStatus._name" :ignoreReason="issue.ignoreReason"
                :issueChecked="(selectedIssueIds.indexOf(issue.id) > -1)" :filterStatus="filterStatus"
                @clickedIssueCheckbox="updateSelectedIssueIds"
                @openDialogBox="openDialogBox">
            </issue-box>
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
            dialogBox: false
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
            , 'filterSubCategory1', 'startEpoch', 'selectedIssueIds','testingRunResult']),

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
    mounted() {
        this.$store.dispatch('issues/loadIssues')
    },
    methods: {
        async openDialogBox({issueId}) {
            await this.$store.dispatch('issues/loadTestingResult',{issueId})
            this.dialogBox = true
        },
        updateSelectedIssueIds({issueId, checked}) {
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
        getCollectionName(collectionId) {
            return this.mapCollectionIdToName[collectionId];
        }
    }
}

</script>

<style scoped >
</style>