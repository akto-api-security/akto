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

                        <v-dialog
                            :model="showDeleteDialog"
                            width="600px"
                        >
                        <template v-slot:activator="{ on, attrs }">
                            <v-btn
                                color="#47466A"
                                icon
                                dark
                                v-bind="attrs"
                                v-on="on"
                                @click="showDeleteDialog = !showDeleteDialog"
                            >
                            <v-tooltip bottom>
                                <template v-slot:activator='{ on, attrs }'>
                                    <v-icon color="#6200EA" size="16" v-bind="attrs" v-on="on" >$fas_trash</v-icon>
                                </template>
                                Delete multiple collections
                            </v-tooltip>
                            </v-btn>
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
                <schedule-box @schedule="scheduleTest"/>
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

export default {
    name: "ApiCollections",
    components: { 
        SimpleTable,
        Spinner,
        SimpleTextField,
        BatchOperation,
        ScheduleBox
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
            return !this.isScheduled(item)
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
        async scheduleTest({recurringDaily, startTimestamp}) {
            await this.$store.dispatch('testing/scheduleTestForCollection', {apiCollectionId: this.scheduleTestCollectionId, startTimestamp, recurringDaily})
            this.showScheduleTestBox = false
        }
    },
    computed: {
        ...mapState('collections', ['apiCollections', 'loading', 'testingRuns']),
        ...mapState('testing', ['testingRuns', 'authMechanism']),
        apiCollectionsForTable() {
            return this.apiCollections.map(c => {
                return {
                    ...c,
                    color: "#FFFFFF",
                    endpoints: (c.urls || []).length,
                    detected: func.prettifyEpoch(c.startTs)
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

</style>

<style lang="sass">
.v-time-picker-clock__item
    color: #47466A

.v-time-picker-clock__item--active
    color: #FFFFFF    
</style>