<template>
    <v-dialog v-model="dialogBoxVariable">
        <div class="details-dialog">
            <a-card title="Issue details" color="var(--rgbaColor2)" subtitle="" icon="$fas_stethoscope">
                <template #title-bar>
                    <v-btn plain icon @click="closeDialogBox()" style="margin-left: auto">
                        <v-icon>$fas_times</v-icon>
                    </v-btn>
                </template>
                <div class="pa-4">
                    <test-results-dialog 
                        :similarlyAffectedIssues="similarlyAffectedIssues" 
                        :issuesDetails="issue" 
                        :subCatogoryMap="subCatogoryMap" 
                        :testingRunResult="testingRunResult" 
                        :mapCollectionIdToName="mapCollectionIdToName"
                        />
                </div>
            </a-card>
        </div>
    </v-dialog>
</template>

<script>
import ACard from '@/apps/dashboard/shared/components/ACard'
import TestResultsDialog from "../../testing/components/TestResultsDialog"
import obj from "@/util/obj"

export default {
    name: 'IssuesDialog',
    components: {
        ACard,
        TestResultsDialog
    },
    props: {
        openDetailsDialog: obj.boolR,
        testingRunResult : obj.objR,
        issue : obj.objR,
        subCatogoryMap: obj.objR,
        similarlyAffectedIssues: obj.arrN
        
    },
    data() {
        return {
            dialogBoxVariable: false
        }
    },
    methods: {
        closeDialogBox() {
            this.$emit('closeDialogBox')
        }
    },
    computed: {
        mapCollectionIdToName() {
            return this.$store.state.collections.apiCollections.reduce((m, e) => {
                m[e.id] = e.displayName
                return m
            }, {})
        }
    },
    watch: {
        openDetailsDialog(newValue) {
            this.dialogBoxVariable = newValue
            if (!newValue) {
                this.$emit('closeDialogBox')
            }
        },
        dialogBoxVariable(newValue) {
            if (!newValue) {
                this.$emit('closeDialogBox')
            }
        }
    }
}

</script>

<style scoped>

</style>