<template>
    <div style="max-height: 200px;">
        <v-list dense class="filter-list" :style="{'width':  width || '250px'}">
            <div v-if="!parent">
                <v-list-item v-for="(item, index) in items" :key="index" class="clickable-bg" @click="clicked(item)">
                    <v-list-item-content>
                        <span class="item-label">{{item.title}}</span>
                        <span class="item-subtitle">{{item.subtitle}}</span>
                    </v-list-item-content>
                </v-list-item>
            </div>
            <div v-else>
                <div class="nested-title-bar">
                    <v-icon :size="12" class="back-btn" @click="backBtnClicked">$fas_arrow-left</v-icon>
                    <div>{{ parent['nested']["title"] }}</div>
                </div>
                <v-divider></v-divider>
                <div>
                    <v-list-item v-for="(item, index) in parent['nested']['values']" :key="index" class="clickable-bg" @click="nestedItemClicked(item)">
                        <v-list-item-content class="nested-content">
                            <span class="item-label">{{item.title}}</span>
                            <span class="item-subtitle">{{item.subtitle}}</span>
                        </v-list-item-content>
                    </v-list-item>
                </div>
            </div>
        </v-list>
    </div>
</template>

<script>
import obj from '@/util/obj'

export default {
    name: "NestedFilterList",
    props: {
        items: obj.arrN,
        width: obj.strN,
    },
    components: {

    },
    data() {
        return {
            parent: null
        }
    },
    methods: {
        clicked(item) {
            if (item['nested']) {
                this.parent = item
            } else {
                this.$emit("clicked", {"parent":item, "nestedValue":null})
            }
        },
        nestedItemClicked(item, key) {
            this.$emit("clicked", {"parent":this.parent, "nestedValue": item})
        },
        backBtnClicked() {
            this.parent = null
        }
    },
    async mounted() {
    },
    destroyed() {
    },
    computed: {

    }
}
</script>

<style lang="sass" scoped>
.filter-list
    overflow-y: scrollbar
    overflow-x: hidden
.item-label
    font-size: 12px
    padding: 0px !important
.item-subtitle
    font-size: 9px
    color: var(--themeColorDark7)
.nested-title-bar
    display: flex
    font-size: 12px
    font-weight: 500
    align-items: center
    text-align: center
    padding: 0px 0px 6px 0px

.nested-content
    padding: 0px 8px 0px 8px !important

.back-btn
    cursor: pointer

</style>
