<template>
    <div>
        <div class="brdb pl-8">
            <div v-if="title" class="pt-6">
                <div>
                    <span class="board-name">{{ title || 'Loading...' }}</span>
                </div>
                <div v-if="description">
                    <span class="board-description">{{description}}</span>
                </div>
            </div>
            <div v-else>
                <slot name="title"/>
            </div>
            <div class="d-flex justify-space-between">
                <div class="pt-4 d-flex jc-sb" style="width: 100%">
                    <div class="tabs-container">
                        <v-tabs
                            active-class="active-tab"
                            slider-color="var(--themeColor)"
                            height="40px"
                            v-model="tabName"
                            :show-arrows="false"
                        >
                            <v-tab class="right-pane-tab" v-for="tab in tabs" :key="tab">
                                {{tab}}
                                <v-chip v-if="tabsContent && tabsContent[tab]" :style="{ 'height': '18px !important' }"
                                 class="ml-2 mr-2" color="#47466AB2" text-color="var(--white)">
                                        {{ tabsContent[tab] }}
                                </v-chip>
                            </v-tab>
                        </v-tabs>
                    </div>
                    <div>
                        <slot name="actions-tray"/>
                    </div>
                </div>
            </div>
        </div>
        <div class="pl-8">
            <v-tabs-items v-model="tabName">
                <v-tab-item class="right-pane-tab-item" v-for="tab in tabs" :key="tab">
                    <slot :name="tab"/>
                </v-tab-item>
            </v-tabs-items>
        </div>
    </div>
</template>

<script>
    import obj from "@/util/obj";

    export default {
        name: "LayoutWithTabs",
        props: {
            title: obj.strN,
            tabs: obj.arrR,
            description: obj.strN,
            defaultTabName: obj.strN,
            tabsContent: obj.objN,
            tab: obj.strN
        },
        data () {
            return {
                tabName: parseInt(this.tab) || null
            }
        },
        methods: {
            reset() {
                this.tabName = 0
            },
            setTabWithName(tabName) {
                this.tabName = this.tabs.indexOf(tabName)
            }
        },
        watch: {
            defaultTabName: function (newVal) {
                console.log(newVal, this.tabs.indexOf(newVal))
                this.tabName = this.tabs.indexOf(newVal)
            }
        }
    }

</script>

<style scoped lang="sass">
.board-name
    font-weight: 600
    font-size: 24px
    color: var(--themeColorDark)

.board-description
    font-weight: 500
    font-size: 13px
    color: var(--themeColorDark)
    opacity: 0.7
    padding-top: 4px

.right-pane-tab
    width: fit-content !important
    min-width: 0
    padding: 0px
    margin: 0 16px 0 0
    font-weight: 400
    font-size: 13px
    color: var(--base)
    opacity: 0.5

    &.active-tab
        font-weight: 500
        opacity: 1

.tabs-container
    padding-left: 0px
</style>