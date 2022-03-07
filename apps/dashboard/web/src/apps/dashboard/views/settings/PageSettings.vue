<template>
    <layout-with-tabs title="Settings" :tabs="['Integrations','Account', 'Users', 'Health']">
      <template slot="Integrations">
        <integrations/>
      </template>
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
        <template slot="Health">
            <health/>
        </template>
    </layout-with-tabs>
</template>

<script>
import LayoutWithTabs from '@/apps/dashboard/layouts/LayoutWithTabs'
import TeamOverview from './components/TeamOverview'
import Health from './components/Health'
import Integrations from './components/Integrations'
import api from './api'
export default {
    name: "PageSettings",
    components: { 
        LayoutWithTabs,
        TeamOverview,
        Health,
        Integrations
    },
    methods: {
        getActiveAccount() {
            return this.$store.state.auth.activeAccount
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