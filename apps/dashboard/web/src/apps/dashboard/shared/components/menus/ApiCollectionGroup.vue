<template>
    <v-list dense nav class="pa-4">
        <slot name="prependItem"/>
        <v-list-group
            v-for="item in items"
            :key="item.title"
            v-model="item.active"
            :group="item.group"
            :color="item.color || $vuetify.theme.themes.dark.themeColor"
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
                v-for="child in item.items"
                :key="child.title"
                :to="child.link"
                :class="['menu-item', child.class || '']"
                active-class="menu-item-active"
            >
            
                <v-list-item-content>
                    <v-list-item-title v-text="child.title" class="menu-item-text"></v-list-item-title>
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
    background: rgba(71, 70, 106, 0.03)
    border-radius: 0px !important
    margin-bottom: 0px !important
    color: #47466A !important
    padding-left: 24px !important

    &.alert
        border-left: 4px solid var(--v-redMetric-base) !important
        padding-left: 20px !important

    &.bold .menu-item-text
        font-weight: 600 !important

    &:before
        border-radius: 0px !important

.menu-item-active
    background: rgba(71, 70, 106, 0.7) !important
    color: #FFFFFF !important

</style>