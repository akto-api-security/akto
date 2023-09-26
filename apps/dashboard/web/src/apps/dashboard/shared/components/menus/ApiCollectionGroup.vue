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

            <v-list-item
                v-for="(child, index) in item.items"
                :key="child.hexId+'_'+index"
                :to="child.link"
                :class="['menu-item', child.class || '']"
                active-class="menu-item-active"
            >
            
                <v-list-item-content>
                    <v-tooltip bottom>
                        <template v-slot:activator="{on, attrs}">
                            <div v-on="on" v-bind="attrs">
                                <v-list-item-title v-text="child.title" class="menu-item-text"></v-list-item-title>
                            </div>
                        </template>
                        <span>{{child.title}}</span>
                    </v-tooltip>
                </v-list-item-content>
            </v-list-item>
        </v-list-group>
    </v-list>    
</template>

<script>

import obj from "@/util/obj"

export default {
    name: "ApiCollectionGroup",
    props: {
        items: obj.arrR
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

.menu-item-active:not(.no-style)
    background: var(--themeColorDark5) !important
    color: var(--white) !important

.no-style
    background: var(--themeColorDark15) !important

</style>