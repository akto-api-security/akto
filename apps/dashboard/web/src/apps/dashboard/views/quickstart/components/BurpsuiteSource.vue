<template>
    <div>
        Use burp plugin to send traffic to Akto and realize quick value. If you like what you see,
        we highly recommend using AWS or GCP traffic mirroring to get real user data for a smooth, automated and minimum
        false positive experience.
        <div class="container mt-3">
            <div class="row">
               <div class="">1.</div><div>Download Akto's Burp extension from <a class="clickable-docs" target="_blank" href="https://akto-setup.s3.amazonaws.com/templates/Akto.jar">here</a></div>
            </div>
            <div class="row">
                <div class="">2.</div><div>Follow <a class="clickable-docs" target="_blank" href="https://docs.akto.io/getting-started/tracking-3rd-party-apis/using-burp-suite">this</a> documentation to send burp traffic to Akto.</div>
            </div>
        </div>
    </div>
</template>

<script>
import BurpSuiteIntegration from './BurpSuiteIntegration.vue'
export default {
    name: "BurpsuiteSource",
    components: {
        BurpSuiteIntegration
    }
}
</script>

<style lang="sass" scoped>
.clickable-docs
    cursor: pointer
    color: #6200B0 !important
    text-decoration: underline

.container
    display: flex
    flex-direction: column
    justify-content: space-between

.row
    display: flex   
    
.row > div:first-child 
    width: 20px
</style>