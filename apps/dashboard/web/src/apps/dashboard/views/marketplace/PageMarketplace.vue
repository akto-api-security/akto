<template>
    <simple-layout title="Tests Library">
        <template>
            <div class="pa-8">
                <div>                
                    <layout-with-left-pane>
                        <div class="category-tests">
                            <router-view :key="$route.fullPath"/>
                        </div>
                        <template #leftPane>
                            <v-navigation-drawer
                                v-model="drawer"
                                floating
                                width="250px"
                            >
                                <div class="nav-section">
                                    <api-collection-group
                                        :items=leftNavItems
                                    >
                                        <!-- <template #prependItem>
                                            <v-btn primary dark color="#6200EA" tile style="width: -webkit-fill-available" class="mt-8 mb-8">
                                                <div style="width: 100%">
                                                    <v-icon>$fas_plus</v-icon> 
                                                    New test
                                                </div>
                                            </v-btn>
                                        </template> -->
                                    </api-collection-group>
                                </div>
                            </v-navigation-drawer>
                        </template>
                    </layout-with-left-pane>
                </div>
            </div>
        </template>

    </simple-layout>
</template>

<script>
import SimpleLayout from '@/apps/dashboard/layouts/SimpleLayout'
import LayoutWithLeftPane from '@/apps/dashboard/layouts/LayoutWithLeftPane'
import ApiCollectionGroup from '@/apps/dashboard/shared/components/menus/ApiCollectionGroup'

import func from '@/util/func'
import { mapState } from 'vuex'

export default {
    name: "PageMarketplace",
    components: { 
        SimpleLayout,
        LayoutWithLeftPane,
        ApiCollectionGroup
    },
    data() {
        return {
            drawer: null
        }
    },
    methods: {
        nameToKvObj(names) {
            return names.map(x => {
                return {
                    text: x,
                    value: x.toLowerCase().replaceAll(" ", "_")
                }
            })
        },
        createCategoryObj(arrCategoryKv, creatorTitle, creatorType, colorType) {
            return {
                icon: "$fas_plus",
                title: creatorTitle,
                group: "/dashboard/library/"+creatorType,
                color: func.actionItemColors()[colorType],
                active: true,
                items: [
                    ...arrCategoryKv.map(category => {
                        return {
                            title: category.text,
                            link: "/dashboard/library/"+creatorType+"/"+category.value,
                            icon: "$fas_plus",
                            active: false
                        }
                    })
                ]
            }
        }
    },
    async mounted() {
        await this.$store.dispatch('marketplace/fetchAllMarketplaceSubcategories')
        this.$router.push(this.leftNavItems[0].items[0].link)
        
    },
    computed: {
        ...mapState('marketplace', ['defaultSubcategories', 'userSubcategories', 'loading']),
        leftNavItems() {
            return [
                this.createCategoryObj(this.nameToKvObj(this.defaultSubcategories), "Categories", "default", "This week"),
                this.createCategoryObj(this.nameToKvObj(this.userSubcategories), "Your tests", "custom", "Total")
            ]
        }
    }
}
</script>

<style lang="sass" scoped>
.category-tests
    color: #47466A
    font-family: Poppins, sans-serif !important

</style>