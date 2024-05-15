<template>
    <spinner v-if="loading" style="width: 40px; height: 40px;"></spinner>
    <div class=" no-pointer-events pa-8" v-else>
        <div v-for='title in titles' :key="title">
            <div class="title">
                {{ title }} connections
                <v-icon color="var(--themeColorDark)">$fas_caret-down</v-icon>
            </div>
            <div v-for="source in getSourcesInOrder()" :key="source.title + title">
                <single-data-source v-if="source.connected === title" :icon=source.icon :title=source.title
                    :detail=source.detail :subtitle=source.subtitle>
                    <template #detail v-if="source.detailComponent">
                        <component :is="source.detailComponent" class="detail-text"></component>
                    </template>
                </single-data-source>
            </div>
        </div>
    </div>
</template>

<script>
import DetailExpansionPanel from './DetailExpansionPanel'
import AwsTrafficMirroring from './AwsTrafficMirroring'
import KubernetesDaemonset from './KubernetesDaemonset'
import Fargate from './Fargate'
import GcpTrafficMirroring from './GcpTrafficMirroring'
import BurpsuiteSource from './BurpsuiteSource'
import PostmanSource from './PostmanSource'
import NginxSource from "./NginxSource"
import SingleDataSource from './SingleDataSource'
import Spinner from '@/apps/dashboard/shared/components/Spinner'
import api from '../api'

export default {
    name: "DataSourcesContent",
    components: {
        DetailExpansionPanel,
        AwsTrafficMirroring,
        SingleDataSource,
        GcpTrafficMirroring,
        BurpsuiteSource,
        PostmanSource,
        Spinner,
        KubernetesDaemonset,
        Fargate,
        NginxSource
    },
    data() {
        return {
            loading: true,
            titles: ["More"],
            sources: {
                "AWS": {
                    icon: "$aws",
                    key: "AWS",
                    title: "AWS Traffic Mirroring",
                    subtitle: 'Recommended',
                    detailComponent: 'AwsTrafficMirroring',
                    connected: "More"
                },
                "DATA_PROCESSORS": {
                    icon: "$fargateIcon",
                    key: "DATA_PROCESSORS",
                    title: "Data processors",
                    subtitle: '',
                    detailComponent: 'Fargate',
                    connected: "More"
                },
                "KUBERNETES": {
                    icon: "$k8s",
                    key: "KUBERNETES",
                    title: "Kubernetes Daemonset",
                    subtitle: '',
                    detailComponent: 'KubernetesDaemonset',
                    connected: "More"
                },
                "GCP": {
                    icon: "$gcp",
                    key: "GCP",
                    title: "GCP Traffic Mirroring",
                    subtitle: 'Recommended',
                    detailComponent: 'GcpTrafficMirroring',
                    connected: "More"
                },
                "BURP": {
                    icon: "$burpsuite",
                    key: "BURP",
                    title: "Burpsuite",
                    detailComponent: 'BurpsuiteSource',
                    connected: "More"
                },
                "POSTMAN": {
                    icon: "$postman",
                    key: "POSTMAN",
                    title: "Postman",
                    detailComponent: 'PostmanSource',
                    connected: "More"
                },
                "NGINX": {
                    icon: "$nginx",
                    key: "NGINX",
                    title: "NGINX",
                    detailComponent: 'NginxSource',
                    connected: "More"
                },
            }
        }
    },
    mounted() {
        this.fetchQuickStartPageState();
    },
    methods: {
        getSourcesInOrder(){
            let order = []
            if (window.DASHBOARD_MODE && window.DASHBOARD_MODE.toLowerCase() === 'local_deploy'){
                order = ['BURP', 'POSTMAN','AWS','GCP', 'KUBERNETES', 'DATA_PROCESSORS', 'NGINX']
            } else {
                order = [ 'AWS','KUBERNETES', 'DATA_PROCESSORS', 'NGINX', 'BURP', 'POSTMAN', 'GCP']
            }
            let final_order = []
            order.forEach(item => {
                final_order.push(this.sources[item]);
            })
            return final_order
        },
        fetchQuickStartPageState() {
            api.fetchQuickStartPageState().then((resp) => {
                if (resp.configuredItems) {
                    if(resp.configuredItems.length > 0) {
                      this.titles = ['My', 'More']
                    }
                    resp.configuredItems.forEach(item => {
                        this.sources[item].connected = "My";
                    });
                }
                this.loading = false;
            })
        }
    }
}
</script>

<style lang="sass" scoped>
.no-pointer-events
    pointer-events: none
    color: var(--themeColorDark)

.title
    font-weight: 600
    font-size: 16px !important

.detail-text
    font-weight: 400
    font-size: 13px

</style>