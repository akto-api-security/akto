<template>
    <div class="pl-8">
        <div>
                <v-breadcrumbs
                    :items="breadcrumbs"
                    class="api-breadcrumbs"
                > 
                    <template v-slot:item="{ item }">
                        <v-breadcrumbs-item
                            @click="navigateFromBreadcrumb(item)"
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
                <router-view @selectedItem="selectedItem"/>
        </div>
            
    </div>
</template>

<script>

import ApiCollections from "../collections/APICollections"
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
                    to: {
                        path: '/dashboard/observe/inventory'
                    }
                }
            ]

        return {
            breadcrumbs: breadcrumbs
        }
    },
    methods: {
        navigateFromBreadcrumb (item) {
            let bcIndex = this.breadcrumbs.indexOf(item)
            this.breadcrumbs = this.breadcrumbs.slice(0, bcIndex+1)
            this.breadcrumbs[bcIndex].disabled = true
            this.$router.push(item.to)
        },
        selectedItem (event) {
            let newRouteObject = {}
            switch (event.type) {
                case 1:
                    newRouteObject = {
                        text: event.collectionName,
                        to: {
                            name: 'apiCollection',
                            params: {
                                apiCollectionId: event.apiCollectionId
                            }
                        }
                    }

                    break;
                case 2:
                    newRouteObject = {
                        text: event.urlAndMethod,
                        to: {
                            name: 'apiCollection/urlAndMethod',
                            params: {
                                apiCollectionId: event.apiCollectionId,
                                urlAndMethod: btoa(event.urlAndMethod)
                            }
                        }
                    }    
            }

            newRouteObject.disabled = true
            this.breadcrumbs[this.breadcrumbs.length-1].disabled = false
            this.breadcrumbs.push(newRouteObject)
            this.breadcrumbs = [...this.breadcrumbs]

            this.$router.push(newRouteObject.to)
            
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
