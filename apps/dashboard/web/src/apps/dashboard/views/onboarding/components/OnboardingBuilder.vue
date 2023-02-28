<template>
    <div class="main">
      <v-container style="padding-top: 80px;">
        <v-row>
          <v-col>
            <onboarding-description
                :title="descriptionArr[currentStep-1].title"
                :subtitle="descriptionArr[currentStep - 1].subtitle"
                :totalSteps="totalSteps"
                :currentStep="currentStep"
            />


            <v-card class="pa-3 main_card" plain :outlined="true">
                <select-collection-component v-if="currentStep === 1"/>
                <select-test-suite v-if="currentStep === 2"/>
                <set-config v-if="currentStep===3"/>
                <next-button 
                    :text="descriptionArr[currentStep-1].buttonText"
                    :icon="descriptionArr[currentStep-1].icon"
                    :prepend="descriptionArr[currentStep-1].prepend"
                    @next="next"
                />
            </v-card>
          </v-col>
        </v-row>

      </v-container>
    </div>
</template>


<script>
import OnboardingDescription from '@/apps/dashboard/views/onboarding/components/OnboardingDescription'
import SelectCollectionComponent from '@/apps/dashboard/views/onboarding/components/SelectCollectionComponent'
import SelectTestSuite from '@/apps/dashboard/views/onboarding/components/SelectTestSuite'
import NextButton from '@/apps/dashboard/views/onboarding/components/NextButton'
import SetConfig from '@/apps/dashboard/views/onboarding/components/SetConfig'

export default {
    name: "OnboardingBuilder",
    components: {
        OnboardingDescription,
        SelectCollectionComponent,
        SelectTestSuite,
        NextButton,
        SetConfig
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
                    "prepend": false
                },
                {
                    "title": "Select Tests",
                    "subtitle": "Select tests you wish to run on your API endpoints.",
                    "buttonText": "Set Config",
                    "icon": "$fas_arrow-right",
                    "prepend": false
                },
                {
                    "title": "Set config",
                    "subtitle": "We have pre-filled token for you!",
                    "buttonText": "Run tests",
                    "icon": "$fas_bolt",
                    "prepend": true
                },
            ]
        }
    },
    methods: {
        next() {
            if (this.currentStep + 1 > this.totalSteps) return
            this.currentStep += 1
        },
    }
}
</script>

<style lang="sass">
.main
    background-color: #F7F7F7
    height: 100%

.main_card
    margin: 0 auto
    border-radius: 12px !important
    border: 0px
    position: relative
    box-shadow: unset
    width: fit-content

</style>