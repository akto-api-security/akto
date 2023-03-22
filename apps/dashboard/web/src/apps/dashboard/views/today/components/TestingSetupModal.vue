<template>
    <a-card
        title="Test setup"
        color="var(--rgbaColor2)"
        subtitle=""
        icon="$fas_stethoscope"
        class="test-setup-modal"
    >
        <template #title-bar>
            <v-btn
                plain
                icon
                @click="resetAndEmit"
                style="margin-left: auto"
            >
                <v-icon>$fas_times</v-icon>
            </v-btn>
        </template>
        <div class="height-1">
            <v-stepper v-model="stepNumber" class="test-settings-stepper">
                <v-stepper-items>
                    <v-stepper-content step="1">
                        <auth-protocol-setup 
                            @completed="completedAuthProtocolSetup" 
                            btnText="Next"
                        />
                    </v-stepper-content>
                    <v-stepper-content step="2">
                        <test-env-setup 
                            @completed="completedTestEnvSetup" 
                            btnText="Start testing"
                        />
                    </v-stepper-content>
                </v-stepper-items>
            </v-stepper>
        </div>
    </a-card>
</template>

<script>

    import ACard from "@/apps/dashboard/shared/components/ACard"
    import TestEnvSetup from "./TestEnvSetup"
    import AuthProtocolSetup from "./AuthProtocolSetup"
    import { mapState } from 'vuex'

    export default {
        name: "TestingSetupModal",
        components: {
            ACard,
            TestEnvSetup,
            AuthProtocolSetup
        },
        data () {
            return {
                stepNumber: 1,
                authProtocolSettings: {},
                testEnvSettings: {}
            }
        },
        methods: {
            resetAndEmit () {
                this.stepNumber = 1
                this.$emit('close', {poll: false})
            },
            completedAuthProtocolSetup (authProtocolSettings) {
                this.authProtocolSettings = authProtocolSettings
                this.stepNumber ++
            },
            completedTestEnvSetup (testEnvSettings) {
                this.testEnvSettings = testEnvSettings
                this.stepNumber = 1
                let testConfig = {
                    authProtocolSettings: this.authProtocolSettings,
                    testEnvSettings: this.testEnvSettings,
                    authProtocolSpecs: this.authProtocol
                }
                this.$store.dispatch('today/saveTestConfig', testConfig)
                
                this.$emit('close', {poll:true})
            }
        },
        computed: {
            ...mapState('today', ['authProtocol'])
        }
    }
</script>

<style scoped lang="sass">
.test-setup-modal
    width: 500px

.height-1
    height: 420px    
    display: flex
    flex-direction: column
    justify-content: center 
    overflow: scroll   

.test-settings-stepper
    box-shadow: unset !important
    overflow: scroll
</style>

<style scoped>
.test-settings-stepper >>> .v-stepper__content {
    padding-top: 8px !important
}
</style>

