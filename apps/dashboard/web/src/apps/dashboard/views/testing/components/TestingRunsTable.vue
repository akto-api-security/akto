<template>
    <div>
        <div class="d-flex jc-start">
            <date-range v-model="dateRange"/>
        </div>
        <server-table 
            :key="refreshTable"
            :headers="testingRunsHeaders" 
            name="Active testing runs" 
            sortKeyDefault="endTimestamp"
            :sortDescDefault="true"
            :pageSize="50"
            @rowClicked="goToTestingRunSummaries"
            :fetchParams="fetchTestingRunsItems"
            :processParams="prepareTableItem"
            :hideDownloadCSVIcon="true"
        >

        </server-table>
    </div>
</template>

<script>
import ServerTable from '@/apps/dashboard/shared/components/ServerTable'
import DateRange from '@/apps/dashboard/shared/components/DateRange'
import func from '@/util/func'
import obj from '@/util/obj'
import testing from '@/util/testing'

import api from '../api'

export default {
    name: "TestingRunsTable",
    props: {
        type: obj.strR
    },
    data() {
        return {
            testingRunsHeaders: [
                {
                    text: 'color',
                    value: '',
                    showFilterMenu: false
                },
                {
                    text: "Test name",
                    value: "apiCollectionName",
                    sortKey: "name",
                    showFilterMenu: false
                },
                {
                    text: "Endpoints",
                    value: "endpoints",
                    showFilterMenu: false
                },
                {
                    text: "Type",
                    value: "type",
                    sortKey: "testingEndpoints.type",
                    showFilterMenu: false
                },
                {
                    text: "Last run",
                    value: "lastRunTs",
                    sortKey: "endTimestamp",
                    showFilterMenu: false
                },
                {
                    text: "Next run",
                    value: "nextRunTs",
                    showFilterMenu: false
                },
                {
                    text: "Started by",
                    value: "userEmail",
                    showFilterMenu: false
                },
                {
                    text: "Frequency",
                    value: "frequency",
                    showFilterMenu: false
                }
            ],
            lastRunTs: { startTimestamp: (func.timeNow() - func.recencyPeriod), endTimestamp: func.timeNow() },
            refreshTable: false
        }
    },
    components: {
        ServerTable,
        DateRange
    },
    methods: {
        getCollectionName(testingEndpoints) {
            return testing.getCollectionName(testingEndpoints, this.mapCollectionIdToName)
        },
        getNextRunTs(state, ts) {
            if (state === "SCHEDULED") {
                return func.toDateStr(new Date(ts * 1000), false)
            } else if (state === "RUNNING") {
                return "Running..."
            } else {
                return "-"
            }
        },
        prepareTableItem(run) {
            let days = parseInt(Math.round(run.periodInSeconds/86400))
            return {
                apiCollectionName: run.name || this.getCollectionName(run.testingEndpoints),
                link: run.hexId,
                endpoints: testing.getEndpoints(run.testingEndpoints),
                type: run.testingEndpoints.type,
                userEmail: run.userEmail,
                frequency: days == 0 ? "-" : (days == 1 ? "1 day" : days + " days"),
                lastRunTs: run.endTimestamp ? func.prettifyEpoch(run.endTimestamp) : "-",
                nextRunTs: this.getNextRunTs(run.state, run.scheduleTimestamp)
            }
        },
        goToTestingRunSummaries(item){
            this.$router.push(item.link)
        },
        async fetchTestingRunsItems(sortKey, sortOrder, skip, limit, filters, filterOperators) {
            filters = {};
            filters.endTimestamp = [this.lastRunTs.startTimestamp, this.lastRunTs.endTimestamp];
            let res = {};
            let now = func.timeNow()
            switch (this.type) {
                case func.testingType().cicd:
                    res = await api.fetchTestingDetails({
                        startTimestamp: 0, endTimestamp: 0, fetchCicd: true, sortKey, sortOrder, skip, limit, filters
                    });
                    break;
                case func.testingType().active:
                    res = await api.fetchTestingDetails({
                        startTimestamp: 0, endTimestamp: 0, fetchCicd: false, sortKey, sortOrder, skip, limit, filters
                    });
                    break;
                case func.testingType().inactive:
                    res = await api.fetchTestingDetails({
                        startTimestamp: Math.min(now - func.recencyPeriod, this.lastRunTs.startTimestamp) , endTimestamp: now, fetchCicd: false, sortKey, sortOrder, skip, limit, filters
                    });
                    break;
            }
            return { endpoints: res.testingRuns, total: res.testingRunsCount }
        },
    },
    computed: {
        mapCollectionIdToName() {
            return this.$store.state.collections.apiCollections.reduce((m, e) => {
                m[e.id] = e.displayName
                return m
            }, {})
        },
        dateRange: {
            get () {
                return [func.toHyphenatedDate(this.lastRunTs.startTimestamp * 1000), func.toHyphenatedDate(this.lastRunTs.endTimestamp * 1000)]
            },
            set(newDateRange) {
                this.lastRunTs.startTimestamp = parseInt(func.toEpochInMs(newDateRange[0]) / 1000)
                this.lastRunTs.endTimestamp = parseInt(func.toEpochInMs(newDateRange[1]) / 1000)
                this.refreshTable = !this.refreshTable;
            }
        }
    }
}
</script>

<style lang="sass" scoped>

</style>