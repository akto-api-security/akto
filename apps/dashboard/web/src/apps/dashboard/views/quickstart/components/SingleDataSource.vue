<template>
    <div class="all-pointer-events">
        <div class="d-flex">
            <div>
                <v-icon size="50" class="panel-icon" v-if="icon">{{ icon }}</v-icon>
                <slot name="icon" v-else></slot>
            </div>
            <div class="mr-4 full-width">
                <div class="mb-4">
                    <span class="panel-title text-primary">
                        <span v-if="title">{{ title }}</span>
                        <slot v-else name="title" />
                    </span>
                    <span class="panel-subtitle text-primary">
                        <span v-if="subtitle">
                            <v-chip small color="var(--themeColor10)" text-color="var(--themeColor5)">{{ subtitle }}</v-chip>
                        </span>
                    </span>
                </div>
                <div class="panel-detail grey-text">
                    <span v-if="detail">{{ detail }}</span>
                    <slot v-else name="detail" />
                </div>
            </div>
        </div>
    </div>
</template>

<script>

import obj from '@/util/obj'

export default {
    name: "SingleDataSource",
    props: {
        icon: obj.strN,
        title: obj.strN,
        subtitle: obj.strN,
        detail: obj.strN
    }
}
</script>

<style lang="sass" scoped>
.panel-icon
    font-size: 50px !important
    min-width: 50px
    margin-right: 24px

.all-pointer-events
    pointer-events: all
    box-shadow: -1px -1px 12px var(--rgbaColor14), 0px 2px 10px var(--rgbaColor14)
    border-radius: 8px
    padding: 32px
    margin: 16px 0px

.panel-title
    font-weight: 700
    font-size: 16px    
    margin-right: 16px

.full-width
    width: 100%
</style>