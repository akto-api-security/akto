<template>
    <div>
        <div class="display-flex ml-8 mt-6 mr-8">
            <div>
                <v-menu offset-y>
                    <template v-slot:activator="{ on, attrs }">
                        <v-btn class="filter-button" v-bind="attrs" v-on="on" primary>
                            <span>{{ selectedStatusName }}</span>
                            <v-icon>$fas_angle-down</v-icon>
                        </v-btn>
                    </template>
                    <filter-list :title="selectedStatusName" :items="statusItems"
                        @clickedItem="clickedStatusItem($event)" hideOperators hideListTitle selectExactlyOne />
                </v-menu>
            </div>
            <div class="display-flex">
                <div v-for="(filterMenu, index) in filterMenus">
                    <v-menu :key="index" offset-y :close-on-content-click="false">
                        <template v-slot:activator="{ on, attrs }">
                            <v-btn class="filter-button mr-3" :ripple="false" v-bind="attrs" v-on="on" primary>
                                <span>{{ filterMenu.text }}
                                    <v-icon :size="14">$fas_angle-down</v-icon>
                                </span>
                            </v-btn>
                        </template>
                        <filter-list :title="filterMenu.text" :items="filterMenu.items"
                            @clickedItem="appliedFilter(filterMenu.value, $event)"
                            @operatorChanged="operatorChanged(filterMenu.value, $event)" hideOperators hideListTitle
                            @selectedAll="selectedAll(filterMenu.value, $event)" ref="issueFilterReference"/>
                    </v-menu>
                </div>
                <div>
                    <v-menu offset-y>
                        <template v-slot:activator="{ on, attrs }">
                            <v-btn class="filter-button mr-3" v-bind="attrs" v-on="on" primary>
                                <span>{{ selectedTimeName }}</span>
                                <v-icon>$fas_angle-down</v-icon>
                            </v-btn>
                        </template>
                        <filter-list :title="selectedTimeName" :items="selectedTime"
                            @clickedItem="clickedTimePeriod($event)" hideOperators hideListTitle selectExactlyOne/>
                    </v-menu>
                </div>
                <div>
                    <v-btn class="filter-button" primary @click="exportReport">
                        <span>Export vulnerability report</span>
                    </v-btn>
                </div>
            </div>
        </div>
        <div v-if="issues.length !== 0 && !(filterStatus.length === 0 || filterStatus.length === 1 && filterStatus[0] === 'FIXED')"
            class="mt-10 mr-14 ml-9" :style="{ 'display': 'flex', 'justify-content': 'space-between', 'height' :'36px' }">
            <span class="ml-6">
                <input type="checkbox" class="global-checkbox-size checkbox-primary" v-model="globalCheckbox" />
                <span class="ml-3 select-all">Select All</span>
            </span>
            <span v-if="(filterStatus.length === 1 && filterStatus[0] === 'IGNORED' && selectedIssueIds.length > 0)">
                <v-btn primary color="var(--v-themeColor-base)" class="white--text ignore-button"
                    @click="bulkReopen()"><span>Reopen</span></v-btn>
            </span>
            <span v-else-if="(filterStatus.length === 1 && filterStatus[0] === 'OPEN' && selectedIssueIds.length > 0)">
                <v-menu offset-y>
                    <template v-slot:activator="{ on, attrs }">
                        <v-btn v-bind="attrs" v-on="on" primary color="var(--v-themeColor-base)" class="white--text ignore-button">
                            <span>Ignore</span>
                            <v-icon>$fas_angle-down</v-icon>
                        </v-btn>
                    </template>
                    <v-list>
                        <v-list-item v-for="(item, index) in ignoreReasons" :key="index" link @click="bulkIgnore(item)">
                            <span>
                                {{ item }}
                            </span>
                        </v-list-item>
                    </v-list>
                </v-menu>
            </span>
        </div>
    </div>
</template>

<script>
import FilterList from '@/apps/dashboard/shared/components/FilterList'
import func from "@/util/func"
import obj from "@/util/obj"

export default {
    name: "IssuesFilters",
    components: {
        FilterList
    },
    props: {
        filterStatus: obj.arrR,
        issues: obj.arrR,
        filterCollectionsId: obj.arrR,
        filterSeverity: obj.arrR,
        filterSubCategory1: obj.arrR,
        selectedIssueIds: obj.arrR,
        startEpoch: obj.numR,
        issuesCategories: obj.arrR,
        categoryToSubCategories: obj.ObjR
    },
    data() {
        var statusItems = [
            { title: "Open", value: "OPEN" },
            { title: "Ignored", value: "IGNORED" },
            { title: "Fixed", value: "FIXED" },
            { title: "All", value: "ALL" }
        ]
        var filterMenus = [
            {
                text: "Severity",
                value: "severity",
                showFilterMenu: false,
                items: [
                    { title: "High", value: "HIGH" }, { title: "Medium", value: "MEDIUM" }, { title: "Low", value: "LOW" }
                ]
            },
            {
                text: "Issue Category",
                value: "issueCategory",
                showFilterMenu: false,
                items: this.issuesCategories
            },
            {
                text: "Collections",
                value: "collections",
                showFilterMenu: false,
                items: []
            }
        ]
        var selectedTime = [
            {
                'value': 'lastDay',
                'epoch': func.timeNow() - 24 * 60 * 60,
                'checked': false,
                'title': "Last 1 day"
            },
            {
                'value': 'lastWeek',
                'epoch': func.timeNow() - 7 * 24 * 60 * 60,
                'checked': false,
                'title': "Last week"
            },
            {
                'value': 'lastMonth',
                'epoch': func.timeNow() - 30 * 24 * 60 * 60,
                'checked': false,
                'title': "Last month"
            },
            {
                'value': 'allTime',
                'epoch': 0,
                'checked': false,
                'title': "All time"
            }
        ]
        const ignoreReasons = [
            "False positive",
            "Acceptable risk",
            "No time to fix"
        ]
        const reOpen = "Reopen"
        return {
            ignoreReasons,
            reOpen,
            filterMenus,
            selectedTime,
            statusItems,
            globalCheckbox: false
        }
    },
    computed: {
        selectedStatusName() {
            if (this.filterStatus.length === 0) {//All issues case
                return this.statusItems[3].title
            }
            let title = ''
            this.statusItems.forEach((item) => {
                if (item.value === this.filterStatus[0]) {
                    title = item.title
                }
            })
            return title
        },
        selectedTimeName() {
            let title = ''
            this.selectedTime.forEach((item) => {
                if (item.epoch === this.startEpoch) {
                    title = item.title
                }
            })
            return title
        },
        mapCollectionIdToName() {
            return this.$store.state.collections.apiCollections.reduce((m, e) => {
                m[e.id] = e.displayName
                return m
            }, {})
        }
    },
    watch: {
        globalCheckbox(newValue) {
            let selectedIssueIds = []
            if (newValue) {// global filter checked case
                this.issues.forEach((issue) => {
                    selectedIssueIds.push(issue.id)
                })
            }
            this.$store.commit('issues/updateSelectedIssueIds', { selectedIssueIds })
        },
        issuesCategories(newValue) {
            this.filterMenus[1].items = newValue
        }
    },
    mounted() {
        this.filterMenus[2].items = this.getCollections1()
    },
    methods: {
        exportReport() {
            let obj = {
                filterStatus: this.$store.state.issues.filterStatus,
                filterCollectionsId: this.$store.state.issues.filterCollectionsId,
                filterSeverity: this.$store.state.issues.filterSeverity,
                filterSubCategory: this.$store.state.issues.filterSubCategory1,
                startEpoch: this.$store.state.issues.startEpoch
            }
            let issuesFilters = JSON.stringify(obj)
            const routeData = this.$router.resolve({name: 'testing-export-html', query: {issuesFilters:btoa(issuesFilters)}});
            window.open(routeData.href, '_blank');
        },
        async bulkReopen() {
            await this.$store.dispatch("issues/bulkUpdateIssueStatus", { selectedIssueIds: this.selectedIssueIds, ignoreReason: '', selectedStatus: "OPEN" });
            this.$store.commit('issues/updateCurrentPage', { 'pageIndex': 1 })
            this.$store.dispatch('issues/loadIssues')
            this.globalCheckbox = false
        },
        async bulkIgnore(ignoreReason) {
            await this.$store.dispatch("issues/bulkUpdateIssueStatus", { selectedIssueIds: this.selectedIssueIds, 'ignoreReason': ignoreReason, selectedStatus: "IGNORED" });
            this.$store.commit('issues/updateCurrentPage', { 'pageIndex': 1 })
            this.$store.dispatch('issues/loadIssues')
            this.globalCheckbox = false
        },
        clickedStatusItem({ item }) {
            let filterStatus = []
            if (item.value !== 'ALL') {
                filterStatus.push(item.value)
            }
            this.$store.commit('issues/updateCurrentPage', { 'pageIndex': 1 })
            this.$store.commit('issues/updateFilters', { filterStatus })
            this.$store.dispatch('issues/loadIssues')
        },
        clickedTimePeriod({ item }) {
            let startEpoch = item.epoch
            this.$store.commit('issues/updateCurrentPage', { 'pageIndex': 1 })
            this.$store.commit('issues/updateFilters', { startEpoch })
            this.$store.dispatch('issues/loadIssues')
        },
        appliedFilter(value, $event) {

            let filterCollectionsId = this.filterCollectionsId
            let filterSeverity = this.filterSeverity
            let filterSubCategory1 = this.filterSubCategory1
            let startEpoch = this.startEpoch
            let item = $event.item

            if (value === "severity") { // Severity filter
                let index = filterSeverity.indexOf(item.value)
                if ($event.checked) {
                    if (index < 0) {
                        filterSeverity.push(item.value)
                    }
                } else {
                    if (index > -1) {
                        filterSeverity.splice(index, 1)
                    }
                }
            } else if (value === "issueCategory") { // Category filter
                if ($event.checked) {
                    this.getSubcategoryArray(item.value).forEach(category => {
                        let index = filterSubCategory1.indexOf(category)
                        if (index < 0) {
                            filterSubCategory1.push(category)
                        }
                    })
                    filterSubCategory1.concat(this.getSubcategoryArray(item.value))
                } else {
                    this.getSubcategoryArray(item.value).forEach(category => {
                        let index = filterSubCategory1.indexOf(category)
                        if (index > -1) {
                            filterSubCategory1.splice(index, 1)
                        }
                    })
                }
            } else if (value === "collections") { // collections filter
                let index = filterCollectionsId.indexOf(item.value)
                if ($event.checked) {
                    if (index < 0) {
                        filterCollectionsId.push(item.value)
                    }
                } else {
                    if (index > -1) {
                        filterCollectionsId.splice(index, 1)
                    }
                }
            } else if (value === "timePeriod") {
                if ($event.checked) {
                    this.selectedTime[item.value].checked = true;
                } else {
                    this.selectedTime[item.value].checked = false;
                }
                startEpoch = 0;
                for (let i = 0; i < this.selectedTime.length; i++) {
                    if (this.selectedTime[i].checked) {
                        startEpoch = this.selectedTime[i].epoch;
                        break;
                    }
                }
            }
            this.$store.commit('issues/updateCurrentPage', { 'pageIndex': 1 })
            this.$store.commit('issues/updateFilters', { filterCollectionsId, filterSeverity, filterSubCategory1, startEpoch })
            this.$store.dispatch('issues/loadIssues')
        },
        selectedAll(value, $event) {

            let filterStatus = this.filterStatus
            let filterCollectionsId = this.filterCollectionsId
            let filterSeverity = this.filterSeverity
            let filterSubCategory1 = this.filterSubCategory1
            let startEpoch = this.startEpoch

            if (value === "status") {
                filterStatus = []
            } else if (value === "severity") {
                filterSeverity = []
            } else if (value === "issueCategory") {
                filterSubCategory1 = []
            } else if (value === "collections") {
                filterCollectionsId = []
            } else if (value === "timePeriod") {
                startEpoch = 0
            }

            this.$store.commit('issues/updateCurrentPage', { 'pageIndex': 1 })
            this.$store.commit('issues/updateFilters', { filterStatus, filterCollectionsId, filterSeverity, filterSubCategory1, startEpoch })
            this.$store.dispatch('issues/loadIssues')
        },
        getSubcategoryArray(superCateogoryName) {
            return this.categoryToSubCategories[superCateogoryName]
        },
        getCollections1() {

            let collections = []
            Object.keys(this.mapCollectionIdToName).forEach(item => {
                collections.push(
                    {
                        title: this.mapCollectionIdToName[item],
                        value: item
                    }
                )
            })
            return collections
        }
    }
}
</script> 

<style scoped >
.display-flex {
    display: flex;
    justify-content: space-between;
}
.select-all {
    font-weight: 500;
    font-size: 14px;
}
.ignore-button {
    font-weight: 500;
    font-size: 12px;
    padding-left: 10px !important;
    padding-right: 10px !important;
}
.global-checkbox-size {
    width: 20px;
    height: 20px;
    display: inline-block;
    vertical-align: middle;
}

.filter-button {
    box-sizing: border-box;

    width: fit-content;
    height: 40px;

    background: var(--white);
    border: 1px solid var(--hexColor22);

    font-weight: 500;
    font-size: 14px;

    box-shadow: 0px 1px 2px var(--rgbaColor12);
    border-radius: 4px;
}
</style>