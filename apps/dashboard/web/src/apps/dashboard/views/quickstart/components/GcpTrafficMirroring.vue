<template>
    <div>
        Use Google packet mirroring to send duplicate stream of traffic to Akto. 
        No performance impact, only mirrored traffic is used to analyze APIs. 
        <a  class="clickable-docs" _target="blank" href="https://docs.akto.io/getting-started/self-hosted-deployment/gcp-deploy">Know more</a>
    </div>
</template>

<script>
export default {
    name: "GcpTrafficMirroring"
}
</script>

<style lang="sass" scoped>
.clickable-docs
    cursor: pointer
    color: #6200B0 !important
    text-decoration: underline 
</style>