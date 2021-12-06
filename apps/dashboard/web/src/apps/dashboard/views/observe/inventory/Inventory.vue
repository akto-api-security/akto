<template>
    <div class="pl-8">
        <div>
                <v-breadcrumbs
                    :items="breadcrumbs"
                    class="api-breadcrumbs"
                >
                    <template v-slot:item="{ item }">
                        <v-breadcrumbs-item
                            @click="item.click"
                            :disabled="item.disabled"
                            :class="item.disabled ? '' : 'clickable'"
                        >
                            {{ item.text }}
                        </v-breadcrumbs-item>
                    </template>
                    <template v-slot:divider>
                        <v-icon>$fas_angle-right</v-icon>
                    </template>

                </v-breadcrumbs>
                <template v-if="breadcrumbs.length <= 2">
                    <api-endpoints @selected=selectedEndpoint />
                </template>
                <template v-else>
                    <api-parameters :urlAndMethod="breadcrumbs[2].text"/>
                </template>   
        </div>
            
    </div>
</template>

<script>

import ApiCollections from "./components/APICollections"
import ApiParameters from "./components/APIParameters"
import ApiEndpoints from "./components/APIEndpoints"
import LayoutWithTabs from "@/apps/dashboard/layouts/LayoutWithTabs"
import obj from "@/util/obj"

export default {
    name: "Inventory",
    components: {
        ApiCollections,
        ApiEndpoints,
        ApiParameters,
        LayoutWithTabs
    },
    props: {
        apiCollectionId: obj.numN
    },
    data () {
        let breadcrumbs = [
                {
                    text: 'All endpoints',
                    click: this.unsetBreadcrumb

                },
                {
                    text: 'Main',
                    click: this.unsetBreadcrumb
                }
            ]

        return {
            breadcrumbs: breadcrumbs
        }
    },
    methods: {
        unsetBreadcrumb() {
            this.breadcrumbs = [...this.breadcrumbs.slice(0, 2)]
        },
        selectedCollection({apiCollectionId}) {
            
        },
        selectedEndpoint(endpointName) {
            let bb = this.breadcrumbs
            bb.push({
                text: endpointName,
                disabled: true,
                click: () => {}
            })
            this.breadcrumbs = [...bb]
        }
    },
    mounted() {
        this.$store.dispatch('inventory/loadAPICollection', { apiCollectionId: this.apiCollectionId})
    },
    computed: {
        
    }
    
}
</script>

<style lang="sass" scoped>
.api-breadcrumbs
    padding-left: 0px 
    padding-bottom: 0px
</style>>
