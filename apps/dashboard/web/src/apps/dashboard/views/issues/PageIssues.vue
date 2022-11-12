<template>
    <simple-layout title="Issues">
        <div>
            <spinner v-if="loading">

            </spinner>

            <issue-box v-else v-for="(issue, index) in issues" :key="index" 
                :creationTime="issue.creationTime"
                :method = "issue.id.apiInfoKey.method"
                :endpoint = "issue.id.apiInfoKey.url"
                :severity = "issue.severity"
            >

            </issue-box>
        </div>
    </simple-layout>
</template>

<script>
import { mapState } from 'vuex'
import SimpleLayout from '@/apps/dashboard/layouts/SimpleLayout'
import IssueBox from './components/IssueBox'
import Spinner from '@/apps/dashboard/shared/components/Spinner'

export default {
    name: "PageIssues",
    components: {
        SimpleLayout,
        IssueBox,
        
    },
    computed: {
        ...mapState('issues', ['issues', 'loading'])
    },
    mounted() {
        this.$store.dispatch('issues/loadIssues')
    }
}

</script>

<style scoped lang="sass">
</style>