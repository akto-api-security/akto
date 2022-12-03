<template>
    <v-dialog v-model="openDetailsDialog">
        <div class="details-dialog">
            <a-card title="Issue details" color="rgba(33, 150, 243)" subtitle="" icon="$fas_stethoscope">
                <template #title-bar>
                    <v-btn plain icon @click="openDetailsDialog = false" style="margin-left: auto">
                        <v-icon>$fas_times</v-icon>
                    </v-btn>
                </template>
                <div class="pa-4">
                    <test-results-dialog :testingRunResult="testingRunResult" />
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
        testingRunResult : obj.objR
    },
    watch: {
        openDetailsDialog(newValue) {
            if (!newValue) {
                this.$emit('closeDialogBox')
            }
        }
    }
}

</script>

<style scoped>

</style>