<template>
    <div class="pl-8">
        <div>
                <v-breadcrumbs
                    :items="breadcrumbs"
                    class="api-breadcrumbs"
                > 
                    <template v-slot:item="{ item }">
                        <v-breadcrumbs-item
                            :disabled="item.disabled"
                            :class="item.disabled ? '' : 'clickable-crumb'"
                            @click="navigateFromBreadcrumb(item)"
                        >
                            <span class="px-2 py-1 clickable">{{ item.text }}</span>
                        </v-breadcrumbs-item>
                    </template>
                    <template v-slot:divider>
                        <v-icon>$fas_angle-right</v-icon>
                    </template>

                </v-breadcrumbs>
                <keep-alive exclude="ApiParameters" >
                    <router-view @selectedItem="selectedItem" @mountedView="mountedView"/>
                </keep-alive>
        </div>
            
    </div>
</template>

<script>

import ApiCollections from "../collections/APICollections"
import ApiParameters from "./components/APIParameters"
import ApiEndpoints from "./components/APIEndpoints"
import LayoutWithTabs from "@/apps/dashboard/layouts/LayoutWithTabs"
import {mapState} from "vuex"
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
        return {
            breadcrumbs: []
        }
    },
    methods: {
        navigateFromBreadcrumb (item) {
            let bcIndex = this.breadcrumbs.indexOf(item)
            this.breadcrumbs = this.breadcrumbs.slice(0, bcIndex+1)
            this.breadcrumbs[bcIndex].disabled = true
            this.$router.push(item.to)
        },
        mountedView (event) {
            this.breadcrumbs = [
                {
                    text: 'All endpoints',
                    to: {
                        path: '/dashboard/observe/inventory'
                    }
                }
            ]
            
            if (event.type >= 1) {
                let coll = this.apiCollections.find(x => x.id === event.apiCollectionId)                    
                if (coll) {
                    let newRouteObject = this.getRouteObject({collectionName: coll.displayName, apiCollectionId: coll.id, type: 1})
                    this.breadcrumbs.push(newRouteObject)
                } else {
                    return
                }
            }

            if (event.type >= 2) {
                let newRouteObject = this.getRouteObject({...event})
                this.breadcrumbs.push(newRouteObject)
            }

            this.breadcrumbs.forEach(b => b.disabled = false)
            this.breadcrumbs[this.breadcrumbs.length-1].disabled = true
            this.breadcrumbs = [...this.breadcrumbs]
        },
        getRouteObject (event) {
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
            return newRouteObject
        },
        selectedItem (e, $event) {
            if($event.ctrlKey || $event.metaKey){
                if(e.type === 1){
                    window.open(`${this.$route.path}` + '/' + `${e.apiCollectionId}`)
                }else if(e.type === 2){
                    window.open(`${this.$route.path}` + '/' + `${btoa(e.urlAndMethod)}`)
                }
            }else{
                this.$router.push(this.getRouteObject(e).to)
            }
        }
    },
    created () {
        this.mountedView({type: 0})
    },
    computed: {
        ...mapState('collections', ['apiCollections'])
    }
    
}
</script>

<style lang="sass" scoped>
.api-breadcrumbs
    padding-left: 0px 
    padding-bottom: 0px

.clickable-crumb
    cursor: pointer
    padding: 0 5px 0 5px

    &:hover 
        text-decoration: underline      
        text-decoration-color: var(--themeColorDark)      
    
</style>>
