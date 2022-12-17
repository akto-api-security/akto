<template>
    <simple-layout title="Marketplace">
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

export default {
    name: "PageMarketplace",
    components: { 
        SimpleLayout,
        LayoutWithLeftPane,
        ApiCollectionGroup
    },
    data() {
        let defaultCategoryNames = ["XSS", "Input validation", "BOLA", "BUA", "Directory traversal", "Path traversal", "Command Injection", "SQL Injection"]
        let userCategoryNames = ["Route fuzzing", "NoSQL Command Injection"]
        return {
            drawer: null,
            defaultTestCategories: this.nameToKvObj(defaultCategoryNames),
            userTestCategories:this.nameToKvObj(userCategoryNames)
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
                group: "/dashboard/marketplace/"+creatorType,
                color: func.actionItemColors()[colorType],
                active: true,
                items: [
                    ...arrCategoryKv.map(category => {
                        return {
                            title: category.text,
                            link: "/dashboard/marketplace/"+creatorType+"/"+category.value,
                            icon: "$fas_plus",
                            active: false
                        }
                    })
                ]
            }
        }
    },
    computed: {
        leftNavItems() {
            return [
                this.createCategoryObj(this.defaultTestCategories, "Categories", "default", "This week"),
                this.createCategoryObj(this.userTestCategories, "Your tests", "custom", "Total")
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