<template>
    <div>
        If your API calls are being routed through NGINX, you can use Akto's NGINX module to send traffic to Akto dashboard.
        You need to setup Akto data processors stack and then install Akto traffic collector module in NGINX.
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
            link:"https://docs.akto.io/traffic-connections/nginx"
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