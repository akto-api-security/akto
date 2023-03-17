<template>
    <layout-with-top-branding-pane>
        <template slot="actions">
            <div class="action-button-group">
                <v-btn outlined style="margin-right: 12px" :height="44" @click="openDocs">
                    <v-icon left>
                        $fas_book
                    </v-icon>
                    <span class="action-button-text">Docs</span>
                </v-btn>
                <v-btn outlined :height="44" @click="openDiscordCommunity">
                    <v-icon left size="18">
                        $discord
                    </v-icon>
                    <span class="action-button-text">Join our Discord</span>
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
    border-color: var(--lighten2)

.action-button-text
    color: var(--themeColorDark)
    font-weight: bold
    font-size: 12px

.action-button-group
    padding-top: 20px
    position: absolute
    right: 112px
</style>