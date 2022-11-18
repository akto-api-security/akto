<template>
    <simple-layout title="Issues">
        <div>
            <spinner v-if="loading">
            </spinner>
            <issue-box v-else v-for="(issue, index) in issues" :key="index" :creationTime="issue.creationTime"
                :method="issue.id.apiInfoKey.method" :endpoint="issue.id.apiInfoKey.url" :severity="issue.severity"
                :collectionName="getCollectionName(issue.id.apiInfoKey.apiCollectionId)"
                :categoryName="getCategoryName(issue.id.testCategory)"
                :categoryDescription="getCategoryDescription(issue.id.testCategory)"
                :testType="getTestType(issue.id.testErrorSource)"
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
        Spinner
    },
    computed: {
        ...mapState('issues', ['issues', 'loading', 'collections'])
    },
    mounted() {
        this.$store.dispatch('issues/loadIssues')
    },
    methods: {
        getTestType(name) {
            switch (name) {
                case 'AUTOMATED_TESTING':
                    return 'testing';
                case 'RUNTIME':
                    return 'runtime'
                default:
                    return ''
            }

        },
        getCategoryName(name) {
            switch (name) {
                case 'BOLA':
                case 'ADD_USER_ID':
                case 'PRIVILEGE_ESCALATION':
                    return 'Broken Object Level Authorization (BOLA)';
                case 'NO_AUTH':
                    return 'Broken User Authentication (BUA)'
                default:
                    return 'Broken Object Level Authorization (BOLA)'
            }
        },
        getCategoryDescription(name) {
            switch (name) {
                case 'BOLA':
                    return 'Attacker can access resources of any user by changing the auth token in request.';
                case 'ADD_USER_ID':
                    return 'Attacker can access resources of any user by adding user_id in URL.';
                case 'PRIVILEGE_ESCALATION':
                    return 'Attacker can access resources of any user by replacing method of the endpoint (eg: changemethod from get to post). This way attacker can get access to unauthorized endpoints.';
                case 'NO_AUTH':
                    return 'API doesn\'t validate the authenticity of token. Attacker can remove the auth token and access the endpoint.'
                default:
                    return ''
            }
        },
        getCollectionName(collectionId) {
            let name = '';
            this.collections.forEach(element => {
                if (element.id == collectionId) {
                    name = element.displayName;
                }
            });
            return name;
        }
    }
}

</script>

<style scoped lang="sass">
</style>