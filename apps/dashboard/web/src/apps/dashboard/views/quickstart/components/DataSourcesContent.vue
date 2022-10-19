<template>
    <div class="no-pointer-events pa-8">
        <div v-for='title in titles' :key="title">
            <div class="title">
                {{title}} connections
                <v-icon color="#47466A">$fas_caret-down</v-icon>
            </div>
            <div v-for="source in sources" :key="source.title+title">
                <single-data-source 
                    v-if="source.connected === title"
                    :icon=source.icon
                    :title=source.title
                    :detail=source.detail
                    :subtitle=source.subtitle
                >
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
import SwaggerSource from './SwaggerSource'
import SingleDataSource from './SingleDataSource'

export default {
    name: "DataSourcesContent",
    components: {
        DetailExpansionPanel,
        AwsTrafficMirroring,
        SingleDataSource,
        GcpTrafficMirroring,
        BurpsuiteSource,
        SwaggerSource
    },
    data () {
        let isConnected = true
        return {
            titles: isConnected ? ["My", "More"] : ["More"],
            sources: [
                {
                    icon: "$aws",
                    title: "AWS Traffic Mirroring",
                    subtitle: 'Recommended',
                    detailComponent: 'AwsTrafficMirroring',
                    connected: "My"
                },
                {
                    icon: "$gcp",
                    title: "GCP Traffic Mirroring",
                    subtitle: 'Recommended',
                    detailComponent: 'GcpTrafficMirroring',
                    connected: "More"                    
                },
                {
                    icon: "$burpsuite",
                    title: "Burpsuite",
                    detailComponent: 'BurpsuiteSource',
                    connected: "More"                    
                },
                {
                    icon: "$swagger",
                    title: "OpenAPI Collection",
                    detailComponent: 'SwaggerSource',
                    connected: "More"                    
                }                
            ]
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