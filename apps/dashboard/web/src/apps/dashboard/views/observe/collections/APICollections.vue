<template>
    <div>   
        <spinner v-if="loading" />      
        <div v-else class="pt-8">
            <simple-table
                :headers="headers" 
                :items="apiCollectionsForTable"  
                :actions="actions"
                name="API Collections" 
                sortKeyDefault="name" 
                :sortDescDefault="false"   
                @rowClicked=rowClicked
                hide-default-footer ="true"
                :hideDownloadCSVIcon="true"
            >
                <template v-slot:add-new-row-btn="{}">
                    <div class="clickable download-csv ma-1">
                        <v-btn icon :disabled=showNewRow :color="$vuetify.theme.themes.dark.themeColor"  @click="showNewRow = true">
                            <v-icon>$fas_plus</v-icon>
                        </v-btn>
                    </div>            
                </template>
                <template v-slot:add-new-row="{}">
                    <template><td/><td>
                    <simple-text-field 
                        :readOutsideClick="true"
                        placeholder="New collection name"
                        @changed="createCollection"
                        @aborted="showNewRow = false"
                        v-if="showNewRow"
                    /></td></template>
                </template>
            </simple-table>
            <v-dialog
                v-model="showScheduleTestBox"
                width="400px"
            >
                <div class="show-schedule-box">
                    <v-time-picker
                        v-model="timePicker"
                        ampm-in-title
                        color="#6200EA"
                        header-color="#6200EA"
                        full-width	
                    />

                    <v-checkbox
                        v-model="recurringDaily"
                        label="Run daily"
                        on-icon="$far_check-square"
                        off-icon="$far_square"
                        class="ml-2"
                        :ripple="false"
                    />


                    <v-btn primary dark color="#6200EA" @click="scheduleTest" class="ma-2">
                        Schedule {{recurringDaily ? "daily" : "today"}} at {{timePicker}}
                    </v-btn>
                </div>
            </v-dialog>
        </div>
    </div>        
</template>

<script>
import SimpleTable from '@/apps/dashboard/shared/components/SimpleTable'
import { mapState } from 'vuex'
import func from '@/util/func'
import Spinner from '@/apps/dashboard/shared/components/Spinner'
import SimpleTextField from '@/apps/dashboard/shared/components/SimpleTextField'

export default {
    name: "ApiCollections",
    components: { 
        SimpleTable,
        Spinner,
        SimpleTextField
    },
    
    data() {
        return { 
            headers: [
                {
                    text: "",
                    value: "color"
                },
                {
                    text: "API Name",
                    value: "displayName"
                },
                {
                    text: "Endpoints",
                    value: "endpoints"
                },
                {
                    text: "Discovered",
                    value: "detected"
                }
            ],
            actions: [ 
                {
                    isValid: item => this.isValidForDelete(item),
                    icon: item => '$fas_trash',
                    text: item => 'Delete Collection',
                    func: item => this.deleteCollection(item),
                    success: (resp, item) => this.successfullyDeleted(resp, item),
                    failure: (err, item) => this.unsuccessfullyDeleted(err, item)
                },
                {
                    isValid: item => this.isValidForTest(item),
                    icon: item => this.getIconForTest(item),
                    text: item => this.getTextForTest(item),
                    func: item => this.executeOperationForTest(item),
                    success: (resp, item) => {},
                    failure: (err, item) => {}
                },
                {
                    isValid: item => this.isValidForSchedule(item),
                    icon: item => this.getIconForSchedule(item),
                    text: item => this.getTextForSchedule(item),
                    func: item => this.executeOperationForSchedule(item),
                    success: (resp, item) => {},
                    failure: (err, item) => {}
                }
            ],
            showNewRow: false,
            deletedCollection: null,
            showScheduleTestBox: false,
            timePicker: "10:00",
            recurringDaily: false
        }
    },
    methods: {
        rowClicked(item) {
            this.$emit("selectedItem", {type: 1, collectionName: item.name, apiCollectionId: item.id})
        },
        createCollection(name) {
          this.$store.dispatch('collections/createCollection', {name})
          this.showNewRow = false
        },
        deleteCollection(item) {
            this.deletedCollection = item.name
            if(confirm("Are you sure you want to delete this collection?")) {
                const summ = this.$store.dispatch('collections/deleteCollection', {apiCollection: item})
                console.log(summ)
                return summ
            }
        },
        successfullyDeleted(resp,item) {
            window._AKTO.$emit('SHOW_SNACKBAR', {
                show: true,
                text: `${this.deletedCollection}` + ` deleted successfully!`,
                color: 'green'
            })
            this.deletedCollection = null
        },
        unsuccessfullyDeleted(resp,item) {
            window._AKTO.$emit('SHOW_SNACKBAR', {
                show: true,
                text: `${this.deletedCollection}` + ` could not be deleted`,
                color: 'red'
            })
            this.deletedCollection = null
        },
        isValidForDelete(item) {
            if(item.id != 0)
                return true;
            else
                return false;
        },
        isValidForTest(item) {
            return !this.isRunning(item)
        },
        isValidForSchedule (item) {
            return true
        },
        isRunning(item) {
            let latestRun = this.testingRuns.find(x => x.testingEndpoints.apiCollectionId === item.id)
            return (latestRun != null && (latestRun.state == "RUNNING" || latestRun.state == "SCHEDULED"))
        },
        isScheduled(item) {
            return this.testingSchedules.find(x => {return x.sampleTestingRun.testingEndpoints.apiCollectionId === item.id}) != null
        },
        getIconForTest(item) {
            return this.isRunning(item) ? "$fas_stop" : "$fas_play"
        },
        getIconForSchedule (item) {
            return this.isScheduled(item) ? "$fas_calendar-times" : "$fas_calendar-plus"
        },
        getTextForTest(item) {
            let latestRun = this.testingRuns.find(x => x.testingEndpoints.apiCollectionId === item.id)

            if (latestRun == null) {
                return "Run first test"
            } else if (latestRun.state === "RUNNING" || latestRun.state === "SCHEDULED") {
                return "Stop test"
            } else {
                return "Run new test. Last started " + func.prettifyEpoch(latestRun.scheduleTimestamp)
            }
        },
        getTextForSchedule(item) {
            if (this.isScheduled(item)) {
                return "Stop schedule"
            } else {
                return "Schedule test"
            }
        },
        async executeOperationForTest (item) {
            if (this.isRunning(item)){
                await this.$store.dispatch('testing/stopTestForCollection', item.id)
            } else {
                await this.$store.dispatch('testing/startTestForCollection', item.id)
            }
        },
        async executeOperationForSchedule(item) {
            if (this.isScheduled(item)) {
                await this.$store.dispatch('testing/stopScheduleForCollection', item.id)
            } else {
                this.showScheduleTestBox = true
                this.scheduleTestCollectionId = item.id
            }
        },
        async scheduleTest() {
            let hours = this.timePicker.split(":")[0]
            let minutes = this.timePicker.split(":")[1]
            let startTimestamp = parseInt(+func.dayStart()/1000) + hours * 60 * 60 + minutes * 60
            await this.$store.dispatch('testing/scheduleTestForCollection', {apiCollectionId: this.scheduleTestCollectionId, startTimestamp, recurringDaily: this.recurringDaily})
            this.showScheduleTestBox = false
        }
    },
    computed: {
        ...mapState('collections', ['apiCollections', 'loading', 'testingRuns']),
        ...mapState('testing', ['testingRuns', 'authMechanism', 'testingSchedules']),
        apiCollectionsForTable() {
            return this.apiCollections.map(c => {
                let [count, ts] = (c.urls[0] || "0_0").split("_")
                return {
                    ...c,
                    color: "#FFFFFF",
                    endpoints: +count,
                    detected: func.prettifyEpoch(c.startTs),
                    lastAdded: +ts === 0 ? "-" : func.prettifyEpoch(+ts)
                }
            })
        }
    },
    mounted () {
        this.$store.dispatch('testing/loadTestingDetails')
        this.$emit('mountedView', {type: 0})
    }
}
</script>

<style lang="sass" scoped>
.default-info
    color: #47466A
    font-size: 12px
    margin-top: 16px

.show-schedule-box
    background: #FFFFFF        
</style>

<style lang="sass">
.v-time-picker-clock__item
    color: #47466A

.v-time-picker-clock__item--active
    color: #FFFFFF    
</style>