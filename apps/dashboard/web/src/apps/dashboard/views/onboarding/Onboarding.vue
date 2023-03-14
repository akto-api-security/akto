<template>
    <layout-with-top-branding-pane>
        <template slot="actions">
            <div style="padding-top: 20px; position: absolute; right: 112px">
                <v-btn outlined style="margin-right: 12px" :height="44" @click="openDocs">
                    <v-icon left>
                        $fas_book
                    </v-icon>
                    <span style="color:#47466A; font-weight: bold; font-size: 12px;">Docs</span>
                </v-btn>
                <v-btn outlined :height="44" @click="openDiscordCommunity">
                    <v-icon left size="18">
                        $discord
                    </v-icon>
                    <span style="color:#47466A; font-weight: bold; font-size: 12px;">Join our Discord</span>
                </v-btn>
            </div>
        </template>
        <template slot="main">
            <onboarding-builder/>
        </template>
    </layout-with-top-branding-pane>
</template>


<script>
import LayoutWithTopBrandingPane from '@/apps/dashboard/layouts/LayoutWithTopBrandingPane'
import OnboardingBuilder from '@/apps/dashboard/views/onboarding/components/OnboardingBuilder.vue'

export default {
    name: "Onboarding",
    components: {
        LayoutWithTopBrandingPane,
        OnboardingBuilder
    },
    methods: {
        openDiscordCommunity() {
            return window.open("https://discord.com/invite/Wpc6xVME4s")
        },
        openDocs() {
            return window.open("https://docs.akto.io")
        }
    },
}
</script>

<style lang="sass">
.v-btn
    border-radius: 8px
    border-color: #DADAE1
</style>