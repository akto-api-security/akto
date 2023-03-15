<template>
        <v-expansion-panel class="pa-4 my-2">
            <v-expansion-panel-header>
                <div class="d-flex">
                    <div class="panel-icon">
                        <v-icon size="50" v-if="icon">{{icon}}</v-icon>
                        <slot name="icon" v-else></slot>  
                    </div>
                    <div class="mr-4">
                        <div>
                            <span class="panel-title text-primary">
                                <span v-if="title">{{title}}</span>
                                <slot v-else name="title"/>
                            </span>
                            <span class="panel-subtitle grey-text">
                                <span v-if="subtitle">{{subtitle}}</span>
                                <slot v-else name="subtitle"/>
                            </span>
                        </div>
                        <div class="panel-detail grey-text">
                            <span v-if="detail">{{detail}}</span>
                            <slot v-else name="detail"/>
                        </div>
                    </div>
                </div>
                <template v-slot:actions v-if="!hideActions">
                    <v-icon size="24">
                        $fas_caret-up
                    </v-icon>
                </template>

            </v-expansion-panel-header>
            <v-expansion-panel-content>
                <slot name="content"/>
            </v-expansion-panel-content>
        </v-expansion-panel>
</template>

<script>

import obj from '@/util/obj'

export default {
    name: "DetailExpansionPanel",
    props: {
        icon: obj.strN,
        title: obj.strN,
        subtitle: obj.strN,
        detail: obj.strN,
        hideActions: obj.boolN
    }
}
</script>

<style lang="sass" scoped>
.panel-title
    font-weight: 600
    font-size: 20px

.panel-subtitle
    font-weight: 400
    font-size: 10px

.panel-detail
    font-weight: 400
    font-size: 13px
    margin-top: 16px
    line-height: 1.5

.panel-icon
    color: var(--themeColor)
    padding-right: 32px
</style>