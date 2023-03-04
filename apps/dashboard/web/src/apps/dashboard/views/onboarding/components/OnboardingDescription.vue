<template>
    <div class="page-text">
        <div class="page-heading">
            {{ title }}
        </div>

        <div class="page-description">
            {{ subtitle }}
        </div>

        <div class="page-steps">
            Step {{ currentStep }} of {{ totalSteps }}
        </div>
        <div style="display: flex; flex-direction: row; align-items: stretch;">
            <div 
                class="step" v-for="idx in this.totalSteps" :key="idx" :class="idx <= currentStep ? 'step-selected':'step-not-selected'"
                @click="goToStep(idx)"
            >
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
        currentStep: obj.numR
    },
    methods: {
        goToStep(index) {
            this.$emit("goToStep", index)
        }
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

.step-selected
    background-color: #47466A

.step-not-selected
    background-color: #DADAE1

.main
    background-color: #F7F7F7
    height: 100%

.page-text
    margin: 0 auto
    position: relative
    width: 420px
    flex: none
    order: 1
    flex: none
    margin-bottom: 24px

.page-heading
    font-weight: 600
    font-size: 32px
    color: #47466A

.page-description
    font-weight: 500
    font-size: 16px
    color: #47466A
    padding: 0px 0px 12px 0px

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

.collection-prompt
    height: 72px
    border: 1px solid #DADAE1
    font-weight: 600
    font-size: 14px
    color: #344054
    border: 2px
    border-radius: 12px !important

.collection-name
    font-weight: 500
    color: #344054
    font-size: 16px
    height: 72px
    line-height: 44px
    padding-left: 16px


</style>