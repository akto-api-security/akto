<template>
    <div>
        Use Google packet mirroring to send duplicate stream of traffic to Akto. 
        No performance impact, only mirrored traffic is used to analyze APIs. 
        <div v-if="!isLocalDeploy"><a class="clickable-docs" target="blank" href="https://docs.akto.io/getting-started/self-hosted-deployment/gcp-deploy">Know more</a></div>
        <div v-if="isLocalDeploy">
            <BannerHorizontal class="mt-3">
              <div slot="content">
                <div>To setup traffic mirroring from GCP, deploy in GCP. Go to <a class="clickable-docs" target="blank" href="https://docs.akto.io/getting-started/self-hosted-deployment/gcp-deploy">docs</a>.</div>
              </div>
            </BannerHorizontal>
        </div>
    </div>
</template>

<script>
import BannerHorizontal from '../../../shared/components/BannerHorizontal.vue'
export default {
    name: "GcpTrafficMirroring",
    components: {
        BannerHorizontal,
    },
    data() {
        return {
            isLocalDeploy: window.DASHBOARD_MODE && window.DASHBOARD_MODE.toLowerCase() === 'local_deploy',
        }
    }
}
</script>

<style lang="sass" scoped>
.clickable-docs
    cursor: pointer
    color: var(--quickStartTheme) !important
    text-decoration: underline 
</style>