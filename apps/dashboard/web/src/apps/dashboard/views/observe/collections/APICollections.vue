<template>
    <div>   
        <spinner v-if="loading" />      
        <div v-else class="pt-8">
            <div v-if="showTrafficHelper" style="padding-left: 28px">
                <add-traffic-prompt/>
            </div>
            <simple-table
                :headers="headers" 
                :items="apiCollectionsForTable"  
                :actions="actions"
                name="API Collections" 
                sortKeyDefault="endpoints"
                :sortDescDefault="true"   
                @rowClicked=rowClicked
                hide-default-footer ="true"
                :hideDownloadCSVIcon="true"
            >
                <template v-slot:add-new-row-btn="{}">
                    <div class="clickable download-csv d-flex">
                        <secondary-button 
                            :disabled=showNewRow 
                            @click="showNewRow = true"
                            icon="$plusIcon"
                            text="Create new collections" />

                        <v-dialog
                            :model="showDeleteDialog"
                            width="600px"
                        >
                        <template v-slot:activator="{ on, attrs }">
                            <secondary-button 
                                @click="showDeleteDialog = !showDeleteDialog"
                                icon="$fas_trash"
                                text="Remove collections" 
                                v-bind="attrs"
                                v-on="on"
                            />
                        </template>
                            <batch-operation 
                                title="Parameters" 
                                :items="apiCollectionsForTable"
                                operation-name="Delete"
                                @btnClicked="deleteMultipleCollections"
                            />
                        </v-dialog>
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
                <schedule-box @schedule="startTest"/>
            </v-dialog>
        </div>
    </div>        
</template>

<script>
import SimpleTable from '@/apps/dashboard/shared/components/SimpleTable'
import { mapState } from 'vuex'
import func from '@/util/func'
import api from './api'
import Spinner from '@/apps/dashboard/shared/components/Spinner'
import SimpleTextField from '@/apps/dashboard/shared/components/SimpleTextField'
import BatchOperation from '../changes/components/BatchOperation'
import ScheduleBox from '@/apps/dashboard/shared/components/ScheduleBox'
import SecondaryButton from '@/apps/dashboard/shared/components/buttons/SecondaryButton'
import AddTrafficPrompt from '../../../shared/components/AddTrafficPrompt.vue'

export default {
    name: "ApiCollections",
    components: { 
        SimpleTable,
        Spinner,
        SimpleTextField,
        BatchOperation,
        ScheduleBox,
        SecondaryButton,
        AddTrafficPrompt
    },
    
    data() {
        return { 
            showDeleteDialog: false,
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
                },
                {
                    text: "Test status",
                    value: "lastTestedAt"
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
            showScheduleTestBox: false
        }
    },
    methods: {
        async deleteMultipleCollections({items}) {
            let noOfItems = Object.keys(items).length
            if (noOfItems > 0) {
                items.forEach(x => x.id = x.value)
                let resp = await api.deleteMultipleCollections(items);
                this.deletedCollection = ""+noOfItems+" collections";
                this.successfullyDeleted();
                this.$store.dispatch('collections/loadAllApiCollections')
            }
        },
        rowClicked(item, $event) {
            this.$emit("selectedItem", {type: 1, collectionName: item.name, apiCollectionId: item.id}, $event)
        },
        createCollection(name) {
          this.$store.dispatch('collections/createCollection', {name})
          this.showNewRow = false
        },
        deleteCollection(item) {
            this.deletedCollection = item.name
            if(confirm("Are you sure you want to delete this collection?")) {
                return this.$store.dispatch('collections/deleteCollection', {apiCollection: item})
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
            return false
        },
        isValidForSchedule (item) {
            return false
        },
        isRunning(item) {
            let latestRun = this.testingRuns.find(x => x.testingEndpoints.apiCollectionId === item.id)
            return (latestRun != null && (latestRun.state == "RUNNING" || latestRun.state == "SCHEDULED"))
        },
        isScheduled(item) {
            return this.testingRuns.find(x => {return x.state === "SCHEDULED" && x.testingEndpoints.apiCollectionId === item.id}) != null
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
            if (!this.isScheduled(item)) {
                return "Schedule test"
            }
        },
        async executeOperationForTest (item) {
            if (!this.isRunning(item)){
                await this.$store.dispatch('testing/startTestForCollection', item.id)
            }
        },
        async executeOperationForSchedule(item) {
            if (!this.isScheduled(item)) {
                this.showScheduleTestBox = true
                this.scheduleTestCollectionId = item.id
            }
        },
        async startTest({recurringDaily, startTimestamp}) {
            await this.$store.dispatch('testing/scheduleTestForCollection', {apiCollectionId: this.scheduleTestCollectionId, startTimestamp, recurringDaily})
            this.showScheduleTestBox = false
        },
        getTestStatus(lastTestedAt, state){
            if(state === 'Running' || state === 'Scheduled'){
                return state;
            }
            return func.prettifyEpochWithNull(lastTestedAt, "Never tested")
        }
    },
    computed: {
        ...mapState('collections', ['apiCollections', 'loading', 'testingRuns']),
        ...mapState('testing', ['testingRuns', 'authMechanism']),
        apiCollectionsForTable() {
            return this.apiCollections.map(c => {
                return {
                    ...c,
                    color: "var(--white)",
                    width: '0px',
                    endpoints: c["urlsCount"] || 0,
                    detected: func.prettifyEpoch(c.startTs),
                    lastTestedAt: this.getTestStatus(c.lastTestedAt, c.state)
                }
            })
        },
        showTrafficHelper() {
            let flag = true;
            this.apiCollections.forEach((c) => {
                if ((c.urlsCount) > 0) flag = false
            })
            return flag
        }
    },
    async mounted () {
        let now = func.timeNow()
        await this.$store.dispatch('testing/loadTestingDetails', {startTimestamp: now - func.recencyPeriod, endTimestamp: now})
        this.$emit('mountedView', {type: 0})
    }
}
</script>

<style lang="sass" scoped>
.default-info
    color: var(--themeColorDark)
    font-size: 12px
    margin-top: 16px

</style>

<style lang="sass">
.v-time-picker-clock__item
    color: var(--themeColorDark)

.v-time-picker-clock__item--active
    color: var(--white)    
</style>

