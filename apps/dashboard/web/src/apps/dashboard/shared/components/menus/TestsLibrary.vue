<template>
    <v-list dense nav class="pa-4">
        <slot name="prependItem"/>
        <v-list-group
            v-for="item in items"
            :key="item.title"
            v-model="item.active"
            :group="item.group"
            :color="item.color || $vuetify.theme.themes.dark.themeColor"
            class="pb-4"
            no-action
        >
            <template v-slot:appendIcon>
                <v-icon >$fas_caret-square-up</v-icon>
            </template>

            <template v-slot:activator>
                <v-list-item-content>
                    <v-list-item-title v-text="item.title"></v-list-item-title>
                </v-list-item-content>
            </template>

            <template 
                v-for="category in getCategories(item)"
            >
                <v-list-item
                    :key='"category+"+category'   
                    class="px-0 pt-4 pb-0 category-item"         
                >
                    <v-list-item-content class="category-content">{{category}}</v-list-item-content>
                </v-list-item>


                <v-list-item
                    v-for="(child, index) in getItemsForList(item, category)"
                    :key="category+'_'+index"
                    :to="child.link"
                    :class="['menu-item', child.class || '']"
                    active-class="menu-item-active"
                >
                
                    <v-list-item-content>
                        <v-list-item-title v-text='child.title.split("/")[1]' class="menu-item-text"></v-list-item-title>
                    </v-list-item-content>
                </v-list-item>

            </template>
        </v-list-group>
    </v-list>    
</template>

<script>

import obj from "@/util/obj"

export default {
    name: "TestsLibrary",
    props: {
        items: obj.arrR
    },
    methods: {
        getItemsForList(item, category) {
            return item.items.filter(x => x.title.indexOf(category) == 0)   
        },
        getCategories(item) {
            return [...new Set(item.items.map(x => x.title.split("/")[0]))]
        }
    },
    computed: {
        
    }
}
</script>

<style lang="sass" scoped>
.menu-item
    padding-left: 24px !important
    background: var(--themeColorDark18)
    border-radius: 0px !important
    margin-bottom: 0px !important
    color: var(--themeColorDark) !important
    padding-left: 24px !important

    .menu-item-text
        font-weight: 400 !important

    &.alert
        border-left: 4px solid var(--v-redMetric-base) !important
        padding-left: 20px !important

    &.bold .menu-item-text
        font-weight: 600 !important
        text-decoration: underline  

    &:before
        border-radius: 0px !important

.menu-item-active
    background: var(--themeColorDark5) !important
    color: var(--white) !important

.category-content
    font-size: 12px
    padding: 0px !important
    font-weight: 500
    color: var(--themeColorDark)

.category-item
    min-height: unset !important
</style>