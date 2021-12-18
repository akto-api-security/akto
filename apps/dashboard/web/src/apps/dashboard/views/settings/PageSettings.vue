<template>
    <layout-with-tabs title="Settings" :tabs="['Account', 'Users']">
        <template slot="Account">
            <div class="pa-8">
                <div class="d-flex">
                    <div class="entry-text">Account ID</div>
                    <div class="entry-value">{{getActiveAccount()}}</div>
                </div>
            </div>
        </template>
        <template slot="Users">
            <team-overview/>
        </template>
    </layout-with-tabs>
</template>

<script>
import LayoutWithTabs from '@/apps/dashboard/layouts/LayoutWithTabs'
import TeamOverview from './components/TeamOverview'
import api from './api'
export default {
    name: "PageSettings",
    components: { 
        LayoutWithTabs,
        TeamOverview
    },
    methods: {
        getActiveAccount() {
            return this.$store.state.auth.activeAccount
        },
        sendInvitationEmail(){
            let spec={
                inviteeName: "Ankush",
                inviteeEmail: "ankush@akto.io",
                websiteHostName: window.location.origin
            }
            api.inviteUsers(spec)
        }
    }
}
</script>

<style lang="sass">
.right-pane-tab-item
    box-shadow: none !important
</style>
<style lang="sass" scoped>
.entry-text
    font-weight: 500
    margin-right: 16px
</style>