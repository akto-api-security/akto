<template>
    <div>
        If your API calls are being routed through NGINX, you can use Akto's NGINX module to send traffic to Akto dashboard.
        This methods is recommended when you have end to end TLS and SSL termination happens at NGINX. You need to setup
        akto mirroring setup on any cloud provider and then install akto traffic module in nginx.
        <div v-if="isLocalDeploy">
            <BannerHorizontal class="mt-3">
                <div slot="content">
                    <div>To setup traffic mirroring from NGINX, go to <a class="clickable-docs" target="blank"
                            :href=link>docs</a>.</div>
                </div>
            </BannerHorizontal>
        </div>
        <div v-else><a class="clickable-docs" target="blank"
            :href=link>Know more</a></div>
    </div>
</template>

<script>
import BannerHorizontal from '../../../shared/components/BannerHorizontal.vue'
export default {
    name: "NginxSource",
    components: {
        BannerHorizontal
    },
    data() {
        return {
            isLocalDeploy: window.DASHBOARD_MODE && window.DASHBOARD_MODE.toLowerCase() === 'local_deploy',
            link:"https://docs.akto.io/add-api-data/integrations/nginx"
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