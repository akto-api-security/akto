<template>
    <simple-layout title="Quick start: Takes 2 mins">
        <v-expansion-panels class="detail-panels">
            <detail-expansion-panel
                title="Connect traffic data"
                detail="Akto is all about automation, which is made possible by connecting to your traffic sources. Real user traffic helps Akto create accurate inventory and run business logic tests on APIs"
            >
                <template #icon>
                    <v-icon size="50" class="invert-sideways" color="var(--themeColor)">$fas_share-alt</v-icon>
                </template>
                <template #subtitle>
                    <span>
                        <span>(Your data remains within your VPC strictly.</span>
                        <span class="clickable-docs"><a target="_blank" href="https://docs.akto.io/what-is-akto/faqs#i-dont-want-the-data-to-leave-my-vpn.-does-akto-support-on-premise-deployment">See how Akto handles data)</a></span>
                    </span>
                </template>
                <template #content>
                    <data-sources-content/>
                </template>
            </detail-expansion-panel>


            <detail-expansion-panel
                title="Invite team members"
                subtitle=""
                detail="Invite team members to analyze and test their APIs for vulnerabilties."
            >
                <template #icon>
                    <v-icon size="50" class="invert-sideways" color="var(--themeColor)">$fas_user-plus</v-icon>
                </template>
                
                <template #content><team-overview></team-overview></template>
            </detail-expansion-panel>
        </v-expansion-panels>
        
    </simple-layout>
</template>

<script>

import SimpleLayout from '@/apps/dashboard/layouts/SimpleLayout'
import DetailExpansionPanel from './components/DetailExpansionPanel'
import DataSourcesContent from './components/DataSourcesContent'
import TeamOverview from '../settings/components/TeamOverview.vue'

export default {
    name: "QuickStart",
    components: {
        SimpleLayout,
        DetailExpansionPanel,
        DataSourcesContent,
        TeamOverview
    }
}
</script>

<style lang="sass" scoped>
.invert-sideways
    transform: scaleX(-1)

.detail-panels
    margin: 16px
    width: unset! important

.clickable-docs
    cursor: pointer
    color: var(--themeColor) !important
    text-decoration: underline 
</style>