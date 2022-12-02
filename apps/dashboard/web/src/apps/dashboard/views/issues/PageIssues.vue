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
            >
            </issues-dialog>
            <issue-box v-for="(issue, index) in issues" :key="index" :creationTime="issue.creationTime"
                :method="issue.id.apiInfoKey.method" :endpoint="issue.id.apiInfoKey.url" :severity="issue.severity"
                :collectionName="getCollectionName(issue.id.apiInfoKey.apiCollectionId)"
                :categoryName="getCategoryName(issue.id.testSubCategory)"
                :categoryDescription="getCategoryDescription(issue.id.testSubCategory)"
                :testType="getTestType(issue.id.testErrorSource)" :issueId="issue.id"
                :issueStatus="issue.testRunIssueStatus" :ignoreReason="issue.ignoreReason"
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
        getSubcategoryArray(superCateogoryName) {
            switch (superCateogoryName) {
                case "BOLA":
                    return [
                        'REPLACE_AUTH_TOKEN'
                        , 'ADD_USER_ID'
                        , 'ADD_METHOD_IN_PARAMETER'
                        , 'ADD_METHOD_OVERRIDE_HEADERS'
                        , 'CHANGE_METHOD'
                        , 'REPLACE_AUTH_TOKEN_OLD_VERSION'
                        , 'PARAMETER_POLLUTION'
                    ]
                case "BUA":
                    return ['REMOVE_TOKENS', 'JWT_NONE_ALGO']
            }
        },
        getCategoryName(name) {
            switch (name) {
                case 'REPLACE_AUTH_TOKEN':
                case 'ADD_USER_ID':
                case 'ADD_METHOD_IN_PARAMETER':
                case 'ADD_METHOD_OVERRIDE_HEADERS':
                case 'CHANGE_METHOD':
                case 'REPLACE_AUTH_TOKEN_OLD_VERSION':
                case 'PARAMETER_POLLUTION':
                    return 'Broken Object Level Authorization (BOLA)';
                case 'REMOVE_TOKENS':
                case 'JWT_NONE_ALGO':
                    return 'Broken User Authentication (BUA)'
                default:
                    return 'Broken Object Level Authorization (BOLA)'
            }
        },
        getCategoryDescription(name) {
            switch (name) {
                case 'REPLACE_AUTH_TOKEN':
                    return 'Attacker can access resources of any user by changing the auth token in request.';
                case 'JWT_NONE_ALGO':
                    return 'Attacker can tamper with the payload of JWT and access protected resources.'
                case 'PARAMETER_POLLUTION':
                    return 'Attacker can access resources of any user by introducing multiple parameters with same name.'
                case 'ADD_USER_ID':
                    return 'Attacker can access resources of any user by adding user_id in URL.';
                case 'REPLACE_AUTH_TOKEN_OLD_VERSION':
                    return 'Attacker can access resources of any user by changing the auth token in request and using older version of an API'
                case 'ADD_METHOD_IN_PARAMETER':
                case 'ADD_METHOD_OVERRIDE_HEADERS':
                case 'CHANGE_METHOD':
                    return 'Attacker can access resources of any user by replacing method of the endpoint (eg: changemethod from get to post). This way attacker can get access to unauthorized endpoints.';
                case 'REMOVE_TOKENS':
                    return 'API doesn\'t validate the authenticity of token. Attacker can remove the auth token and access the endpoint.'
                default:
                    return ''
            }
        },
        getCollectionName(collectionId) {
            return this.mapCollectionIdToName[collectionId];
        }
    }
}

</script>

<style scoped >
</style>