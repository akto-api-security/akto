<template>
    <spinner v-if="loading" style="width: 40px; height: 40px;"></spinner>
    <div class=" no-pointer-events pa-8" v-else>
        <div v-for='title in titles' :key="title">
            <div class="title">
                {{ title }} connections
                <v-icon color="#47466A">$fas_caret-down</v-icon>
            </div>
            <div v-for="source in sources" :key="source.title + title">
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
import GcpTrafficMirroring from './GcpTrafficMirroring'
import BurpsuiteSource from './BurpsuiteSource'
import PostmanSource from './PostmanSource'
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
        Spinner
    },
    data() {
        let isConnected = true
        return {
            loading: true,
            titles: isConnected ? ["My", "More"] : ["More"],
            sources: [
                {
                    icon: "$aws",
                    key: "AWS",
                    title: "AWS Traffic Mirroring",
                    subtitle: 'Recommended',
                    detailComponent: 'AwsTrafficMirroring',
                    connected: "More"
                },
                {
                    icon: "$gcp",
                    key: "GCP",
                    title: "GCP Traffic Mirroring",
                    subtitle: 'Recommended',
                    detailComponent: 'GcpTrafficMirroring',
                    connected: "More"
                },
                {
                    icon: "$burpsuite",
                    key: "BURP",
                    title: "Burpsuite",
                    detailComponent: 'BurpsuiteSource',
                    connected: "More"
                },
                {
                    icon: "$postman",
                    key: "POSTMAN",
                    title: "Postman",
                    detailComponent: 'PostmanSource',
                    connected: "More"
                }
            ]
        }
    },
    mounted() {
        this.fetchQuickStartPageState();
    },
    methods: {
        fetchQuickStartPageState() {
            api.fetchQuickStartPageState().then((resp) => {
                this.loading = false;
                if (resp.configuredItems) {
                    resp.configuredItems.forEach(item => {
                        for (let i = 0; i < this.sources.length; i++) {
                            if (this.sources[i].key === item) {
                                this.sources[i].connected = "My";
                            }
                        }
                    });
                }
            })
        }
    }
}
</script>

<style lang="sass" scoped>
.no-pointer-events
    pointer-events: none
    color: #47466A

.title
    font-weight: 600
    font-size: 16px !important

.detail-text
    font-weight: 400
    font-size: 13px

</style>