<template>
    <layout-with-tabs :title="details.teamName || 'Loading...'" :tabs="['Overview', 'Billing']">
        <template slot="Overview">
            <overview/>
        </template>
        <template slot="Billing">

        </template>
    </layout-with-tabs>
</template>

<script>
    import LayoutWithTabs from "../../layouts/LayoutWithTabs";
    import Overview from "./components/Overview";
    import func from "@/util/func";
    import {mapState} from "vuex";

    export default {
        name: "PageTeam",
        components: {
            LayoutWithTabs,
            Overview
        },
        props: {
            id: {
                type: Number,
                required: true
            }
        },
        data () {
            return {

            }
        },
        methods: {
        },
        async mounted () {
            if (func.timeNow() - this.$store.state.team.fetchTs > 3600 || this.$store.state.team.id !== this.id) {
                this.$store.dispatch('team/emptyState')
                await this.$store.dispatch('team/getTeamData', this.id)
            }
        },
        computed: {
            ...mapState('team', ['details'])
        }
    }

</script>

<style scoped lang="sass">

</style>