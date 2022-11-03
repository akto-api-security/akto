<template>
    <div>
        Use burp plugin to send traffic to Akto and realize quick value. If you like what you see,
        we highly recommend using AWS or GCP traffic mirroring to get real user data for a smooth, automated and minimum
        false positive experience.
        <a class="clickable-docs" _target="blank"
            href="https://docs.akto.io/getting-started/tracking-3rd-party-apis/using-burp-suite">Know more</a>
        <burp-suite-integration></burp-suite-integration>
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
</style>