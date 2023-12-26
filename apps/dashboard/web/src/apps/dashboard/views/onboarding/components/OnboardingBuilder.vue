<template>
    <div class="main">
      <div v-if="loading" class="spinner-div">
            <spinner :size="50" color="var(--themeColor)"/>
      </div>
      <v-container style="padding-top: 80px;" v-else>
        <v-row>
          <v-col>
            <onboarding-description
                :title="descriptionArr[currentStep-1].title"
                :subtitle="descriptionArr[currentStep - 1].subtitle"
                :totalSteps="totalSteps"
                :currentStep="currentStep"
                @goToStep="goToStep"
                :showStepBuilder="descriptionArr[currentStep-1].showStepBuilder"
                :centerAlignText="descriptionArr[currentStep-1].centerAlignText"
            />


            <v-card class="pa-3 main_card" plain :outlined="true">
                <select-collection-component v-if="currentStep === 1 && !this.testingRunHexId"/>
                <select-test-suite v-if="currentStep === 2 && !this.testingRunHexId"/>
                <set-config v-if="currentStep===3 && !this.testingRunHexId"/>
                <testing-results-summary v-if="this.testingRunHexId" :testingRunHexId="testingRunHexId"/>
                <next-button 
                    :text="descriptionArr[currentStep-1].buttonText"
                    :icon="descriptionArr[currentStep-1].icon"
                    :prepend="descriptionArr[currentStep-1].prepend"
                    :disabled="nextButtonDisable"
                    @next="next"
                    :loading="runTestLoading"
                />
            </v-card>

            <div class="skip-text" @click="skipOnboarding">
                I am a ninja. I don't need onboarding
            </div>
          </v-col>
        </v-row>

      </v-container>
    </div>
</template>


<script>
import OnboardingDescription from '@/apps/dashboard/views/onboarding/components/OnboardingDescription'
import SelectCollectionComponent from '@/apps/dashboard/views/onboarding/components/SelectCollectionComponent'
import SelectTestSuite from '@/apps/dashboard/views/onboarding/components/SelectTestSuite'
import TestingResultsSummary from '@/apps/dashboard/views/onboarding/components/TestingResultsSummary'
import NextButton from '@/apps/dashboard/views/onboarding/components/NextButton'
import SetConfig from '@/apps/dashboard/views/onboarding/components/SetConfig'
import {mapState} from 'vuex'
import Spinner from '@/apps/dashboard/shared/components/Spinner'

export default {
    name: "OnboardingBuilder",
    components: {
        OnboardingDescription,
        SelectCollectionComponent,
        SelectTestSuite,
        TestingResultsSummary,
        NextButton,
        SetConfig,
        Spinner
    },
    data () {
        return {
            currentStep: 1,
            totalSteps: 3,
            descriptionArr: [
                {
                    "title": "Welcome to Akto",
                    "subtitle": "Add API collection you want to test. Here we have an existing API collection for you.",
                    "buttonText": "Select tests",
                    "icon": "$fas_arrow-right",
                    "prepend": false,
                    "centerAlignText": false,
                    "showStepBuilder": true
                },
                {
                    "title": "Select tests",
                    "subtitle": "Select tests you wish to run on your API endpoints.",
                    "buttonText": "Set Config",
                    "icon": "$fas_arrow-right",
                    "prepend": false,
                    "centerAlignText": false,
                    "showStepBuilder": true
                },
                {
                    "title": "Set config",
                    "subtitle": "We have pre-filled token for you!",
                    "buttonText": "Run tests",
                    "icon": "$fas_bolt",
                    "prepend": true,
                    "centerAlignText": false,
                    "showStepBuilder": true
                },
                {
                    "title": "Test results",
                    "subtitle": "Here are the results for the tests you recently ran",
                    "buttonText": "See all issues",
                    "icon": "$fas_arrow-right",
                    "prepend": false,
                    "centerAlignText": true,
                    "showStepBuilder": false
                },
            ],
            loading: false,
        }
    },
    methods: {
        next() {
            if (this.currentStep === 3) {
                this.$store.commit("onboarding/UPDATE_RUN_TEST_LOADING",true)
                this.$store.dispatch("onboarding/runTestOnboarding")
            } else if (this.currentStep === 4) {
                this.$router.push('/dashboard/testing/' + this.testingRunHexId)
            }
            if (this.currentStep + 1 > this.totalSteps) return
            this.currentStep += 1
        },
        goToStep(index) {
            if (this.nextButtonDisable && index > this.currentStep) return
            this.currentStep = index
        },
        skipOnboarding() {
            this.loading = true
            this.$store.dispatch("onboarding/skipOnboarding").then((resp) => {
                window.location.href = "/dashboard/quick-start"
            })
        }
    },
    mounted() {
        this.$store.dispatch("onboarding/fetchTestSuites")
    },
    computed: {
        ...mapState('onboarding', ['selectedTestSuite', 'selectedCollection', 'runTestLoading', 'testingRunHexId']),
        nextButtonDisable() {
            if (this.currentStep === 1) return !Boolean(this.selectedCollection)
            if (this.currentStep === 2) return !Boolean(this.selectedTestSuite)
            if (this.currentStep === 3) return !Boolean(this.selectedTestSuite)
            if (this.currentStep === 4) return false
        }
    },
    watch: {
        testingRunHexId(newValue) {
            if (newValue) {
                this.currentStep = 4
            }
        }
    },
    destroyed() {
        this.$store.dispatch('onboarding/unsetValues')
    }
}
</script>

<style lang="sass">
.main
    background-color: var(--hexColor41)
    height: 100%

.main_card
    margin: 0 auto
    border-radius: 12px !important
    border: 0px
    position: relative
    box-shadow: unset
    width: fit-content

.skip-text
    margin-top: 32px
    display: flex
    justify-content: center
    font-weight: 600
    font-size: 12px
    color: #AAAAAA
    text-decoration: underline
    cursor: pointer

.spinner-div
    display: flex
    justify-content: center
    height: 100%
    align-items: center

</style>