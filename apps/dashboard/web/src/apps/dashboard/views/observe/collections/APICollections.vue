<template>
    <div>   
        <spinner v-if="loading" />      
        <div v-else class="pt-8">
            <simple-table
                :headers="headers" 
                :items="apiCollectionsForTable"  
                :actions="actions"
                name="API Collections" 
                sortKeyDefault="endpoints" 
                :sortDescDefault="true"   
                @rowClicked=rowClicked
                hide-default-footer="true"
                :hideDownloadCSVIcon="true"
            >
                <template v-slot:add-new-row-btn="{}">
                    <div class="clickable download-csv d-flex">
                        <v-dialog 
                            v-model="showCreateDialog"
                            width="700px"
                            style="background-color: #FFF;"
                        >
                            <template v-slot:activator="{ on, attrs }">
                                <secondary-button 
                                    :disabled=showNewRow 
                                    icon="$plusIcon"
                                    text="Create new collections" 
                                    v-bind="attrs"
                                    v-on="on"
                                />
                            </template>
                            <a-card 
                                title="Add collection" 
                                icon="$fas_cog"
                                subtitle="Praesent et gravida felis. Sed vitae iaculis dolor, nec sodales mauris. gravida felis. Sed vitae"
                                class="ma-0"
                            >
                                <template #title-bar>
                                    <v-btn plain icon @click="closeDialogBox()" style="margin-left: auto">
                                        <v-icon>$fas_times</v-icon>
                                    </v-btn>
                                </template>

                                <div class="pa-4" style="min-height: 500px">
                                    
                                    <div class="d-flex" style="gap: 10px; justify-content: flex-start">
                                        <div class="heading">Collection name:</div>
                                        <div style="flex: 1 0 auto; position: relative">
                                            <name-input 
                                                defaultName="New collection" 
                                                :defaultSuffixes="[]" 
                                                :class='[collectionExists ? "error-state" : ""]'
                                                @changed="setNewCollectionName"
                                            />
                                            <div v-if="collectionExists" class="error-msg" style="position: absolute; top: -15px">
                                                Collection name already exists. Please provide a new name
                                            </div>
                                        </div>
                                    </div>
                                    <div>
                                        <span class="grey-text fs-12">
                                            <span v-if="loadingMatchedEndpoints">calculating matched endpoints...</span>
                                            <span v-else>{{matchedEndpoints}} endpoints matched</span> 
                                        </span>
                                        <span><spinner v-if="loadingMatchedEndpoints"/></span>
                                    </div>

                                    <div>
                                        <div style="padding: 24px; height: 100%">
                                            <v-container>
                                                <test-roles-condition :collection-name="newCollectionName" @matchedEndpoints="getMatchedEndpoints" />
                                            </v-container>
                                        </div>
                                    </div>
                                </div>
                            </a-card>
                        </v-dialog>

                        <v-dialog
                            v-model="showDeleteDialog"
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
import CreateApiCollection from './CreateApiCollection'
import NameInput from '@/apps/dashboard/shared/components/inputs/NameInput'
import ACard from '@/apps/dashboard/shared/components/ACard'
import TestRolesCondition from '../../testing/components/test_roles/components/TestRolesCondition.vue'

export default {
    name: "ApiCollections",
    components: { 
        SimpleTable,
        Spinner,
        SimpleTextField,
        BatchOperation,
        ScheduleBox,
        SecondaryButton,
        CreateApiCollection,
        NameInput,
        ACard,
        TestRolesCondition
    },
    
    data() {
        return {
            showDeleteDialog: false,
            showCreateDialog: false,
            newCollectionName: "",
            matchedEndpoints: 0,
            loadingMatchedEndpoints: false,
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
                    text: "Author",
                    value: "author"
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
        closeDialogBox() {
            this.showCreateDialog = !this.showCreateDialog
        },
        setNewCollectionName(name) {
            this.newCollectionName = name
        },
        getMatchedEndpoints(value){
            this.matchedEndpoints = value
        },
        async deleteMultipleCollections({items}) {
            let noOfItems = Object.keys(items).length
            if (noOfItems > 0) {
                items.forEach(this.buildRequestForMultiDelete)
                let resp = await api.deleteMultipleCollections(items);
                this.deletedCollection = ""+noOfItems+" collections";
                this.successfullyDeleted();
                this.$store.dispatch('collections/loadAllApiCollections')
            }
        },
        buildRequestForMultiDelete(item, index, arr) {
            arr[index].id = arr[index].value
            arr[index].isLogicalGroup = false
            for (const element of this.apiCollectionsForTable) {
                if (element.id == arr[index].id) {
                    if (element.isLogicalGroup != null) {
                        arr[index].isLogicalGroup = element.isLogicalGroup
                    }
                }
            }
        },
        rowClicked(item) {
            this.$emit("selectedItem", {type: 1, collectionName: item.name, apiCollectionId: item.id, isLogicalGroup: item.isLogicalGroup})
        },
        deleteCollection(item) {
            this.deletedCollection = item.name
            if(confirm("Are you sure you want to delete this collection?")) {
                const summ = this.$store.dispatch('collections/deleteCollection', {apiCollection: item})
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
                    author: c["createdBy"] || "System"
                }
            })
        },
        collectionExists() {
            let obj = this.apiCollections.find(x => x.displayName.toLowerCase() === this.newCollectionName)
            return !!obj
        }
    },
    mounted () {
        let now = func.timeNow()
        this.$store.dispatch('testing/loadTestingDetails', {startTimestamp: now - func.recencyPeriod, endTimestamp: now})
        this.$emit('mountedView', {type: 0})
    }
}
</script>

<style lang="sass" scoped>
.default-info
    color: var(--themeColorDark)
    font-size: 12px
    margin-top: 16px

.error-state
    border: 1px solid var(--redMetric)

.error-msg
    color: var(--redMetric)
    font-size: 10px

.heading
    font-weight: 500
    font-size: 14px
    color: var(--themeColorDark)
    min-width: fit-content
    max-width: fit-content
    margin: auto   

</style>

<style lang="sass">
.v-time-picker-clock__item
    color: var(--themeColorDark)

.v-time-picker-clock__item--active
    color: var(--white)    
</style>

