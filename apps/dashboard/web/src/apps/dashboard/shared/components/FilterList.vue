<template>
    <div>
        <div class="list-header">
            <div>{{title}}</div>
            <div>{{Object.values(checkedMap).filter(x => x).length}}/{{Object.values(checkedMap).length}}</div>
        </div>
        <v-list dense class="filter-list">
            <v-list-item v-for="(item, index) in items" :key="index">
                <span>
                    <v-btn icon primary plain :ripple="false" @click="checkboxClicked(item)" class="checkbox-btn">
                        <v-icon>
                            {{checkedMap[item]? '$far_check-square': '$far_square'}}
                        </v-icon>
                    </v-btn>
                </span>
                <v-list-item-content class="item-label">{{item}}</v-list-item-content>
            </v-list-item>
        </v-list>
    </div>    
</template>

<script>

import obj from '@/util/obj'

export default {
    name: "FilterList",
    props: {
        title: obj.strR,
        items: obj.arrN
    },
    data () {
        return {
            checkedMap: this.items.reduce((m, i) => {
                m[i] = false
                return m
            }, {})
        }
    },
    methods: {
        checkboxClicked(item) {
            this.checkedMap[item] = !this.checkedMap[item]
            this.$emit('clickedItem', {item: item, checked: this.checkedMap[item]})
        }
    }
}
</script>

<style lang="sass" scoped>
.item-label
    font-size: 12px
    padding: 0px !important
.checkbox-btn
    min-height: 24px !important
    color: #6200EA !important
.filter-list
    height: 350px
    width: 250px    
    overflow-y: scrollbar
    overflow-x: hidden
.list-header
    border-bottom: 1px solid #6200EA    
    font-weight: 500
    display: flex
    justify-content: space-between
    padding: 8px 16px
    color: #47466A
    background: white
    opacity: 1
    font-size: 14px

</style>