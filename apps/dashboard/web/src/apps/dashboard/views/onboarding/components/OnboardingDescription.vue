<template>
    <div class="page-text">
        <div class="page-heading" :style="[this.centerAlignText? {'text-align': 'center'} : {}]">
            {{ title }}
        </div>

        <div class="page-description" :style="[this.centerAlignText? {'text-align': 'center'} : {}]">
            {{ subtitle }}
        </div>

        <div v-if="this.showStepBuilder">
            <div class="page-steps">
                Step {{ currentStep }} of {{ totalSteps }}
            </div>
            <div class="step-builder">
                <div 
                    class="step" v-for="idx in this.totalSteps" :key="idx" :class="idx <= currentStep ? 'step-selected':'step-not-selected'"
                    @click="goToStep(idx)"
                >
                </div>
            </div>
        </div>
    </div>
</template>


<script>
import obj from "@/util/obj";

export default {
    name: "OnboardingDescription",
    components: {
    },
    props: {
        title: obj.strR,
        subtitle: obj.strR,
        totalSteps: obj.numR,
        currentStep: obj.numR,
        showStepBuilder: obj.boolR,
        centerAlignText: obj.boolR,
    },
    methods: {
        goToStep(index) {
            this.$emit("goToStep", index)
        }
    },
    mounted() {
        
    }
}
</script>

<style lang="sass">

.step
    display: inline-block
    height: 5px
    flex: 1
    margin-right: 5px
    cursor: pointer

.step-builder
    display: flex
    flex-direction: row
    align-items: stretch

.step-selected
    background-color: var(--themeColorDark)

.step-not-selected
    background-color: var(--lighten2)

.page-text
    margin: 0 auto
    position: relative
    width: 420px
    flex: none
    order: 1
    margin-bottom: 24px

.page-heading
    font-weight: 600
    font-size: 32px
    color: var(--themeColorDark)

.page-description
    font-weight: 500
    font-size: 16px
    color: var(--themeColorDark)
    padding: 8px 0px 12px 0px

.page-steps
    color: #AAAAAA
    font-weight: 500
    font-size: 14px
    padding: 0px 0px 8px 0px

.page-login__card
    max-width: 482px
    margin: 0 auto
    border-radius: 12px !important
    border: 0px
    position: relative
    box-shadow: unset
    height: 240px

</style>
