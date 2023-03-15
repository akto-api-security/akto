<template>
    <div>
        <div v-if="testResults && Object.keys(testResults).length > 0"> 
            <test-results-table
                color="var(--themeColor)"
                title=""
                :rows="testResults || []"
                @rowClicked=rowClicked
            />
        </div>
    </div>
</template>

<script>
    import TestResultsTable from "@/apps/dashboard/shared/components/TestResultsTable"
    import { mapState } from 'vuex'

    export default {
        name: "APITestReport",
        components: {
            TestResultsTable
        },
        methods: {
            rowClicked(row) {
                console.log(row)
            }
        },
        computed: {
            ...mapState('today', ['testResults'])
        }
        
    }
</script>

<style scoped>

</style>
