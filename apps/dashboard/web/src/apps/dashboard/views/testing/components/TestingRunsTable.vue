<template>
    <div>
        <simple-table
            :headers="testingRunsHeaders" 
            :items="testingRunsItems" 
            name="Active testing runs" 
            sortKeyDefault="createdTs" 
            :sortDescDefault="true" 
            @rowClicked="goToTestingRunSummaries"
        >

        </simple-table>
    </div>
</template>

<script>
import SimpleTable from '@/apps/dashboard/shared/components/SimpleTable'
import func from '@/util/func'
import obj from '@/util/obj'
import testing from '@/util/testing'

import {mapState} from 'vuex'

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
                    value: ''
                },
                {
                    text: "Test name",
                    value: "apiCollectionName"
                },
                {
                    text: "Endpoints",
                    value: "endpoints"
                },
                {
                    text: "Type",
                    value: "type"
                },
                {
                    text: "Last run",
                    value: "lastRunTs"
                },
                {
                    text: "Next run",
                    value: "nextRunTs"
                },
                {
                    text: "Started by",
                    value: "userEmail"
                },
                {
                    text: "Frequency",
                    value: "frequency"
                }
            ]
        }
    },
    components: {
        SimpleTable
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
                link: run.hexId+'/results',
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
        }
    },
    computed: {
        ...mapState('testing', ['testingRuns', 'pastTestingRuns','cicdTestingRuns']),
        mapCollectionIdToName() {
            return this.$store.state.collections.apiCollections.reduce((m, e) => {
                m[e.id] = e.displayName
                return m
            }, {})
        },
        testingRunsItems() {
            switch(this.type){
                case func.testingType().cicd: return this.cicdTestingRuns.map(run => this.prepareTableItem(run));
                case func.testingType().active: return (this.testingRuns || []).map(run => this.prepareTableItem(run));
                case func.testingType().inactive: return (this.pastTestingRuns || []).map(run => this.prepareTableItem(run));
            }
        }
    }
}
</script>

<style lang="sass" scoped>

</style>